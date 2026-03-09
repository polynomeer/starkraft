package authoritative

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"math"
	"net"
	"net/http"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/starkraft/server/pkg/protocol"
)

type Config struct {
	Addr                       string
	SimVersion                 string
	BuildHash                  string
	TickInterval               time.Duration
	ReplayPath                 string
	KeyframeEvery              int
	MaxReadBytes               int64
	MaxBatchCommands           int
	MaxRequestIDLength         int
	MaxUnitIDsPerCommand       int
	MaxClientNameLength        int
	MaxRoomIDLength            int
	MaxPendingBatchesPerClient int
	MaxOutboundQueue           int
	EmptyRoomTTL               time.Duration
	ResumeWindow               time.Duration
}

type Server struct {
	cfg            Config
	upgrader       websocket.Upgrader
	rooms          map[string]*room
	roomMatchEnded map[string]bool
	nextID         int
	mu             sync.Mutex
	httpSrv        *http.Server
	replay         *ReplayWriter
	tickSamples    []int64
	tickCursor     int
	tickCount      int
	maxPendingSeen int
	resumeSessions map[string]resumeSession
	nextResumeID   int
}

type clientConn struct {
	id          string
	name        string
	roomID      string
	simVersion  string
	buildHash   string
	resumeToken string
	conn        *websocket.Conn
	writeMu     sync.Mutex
}

type resumeSession struct {
	clientID string
	name     string
	roomID   string
	expires  time.Time
}

type healthResponse struct {
	Status          string `json:"status"`
	ProtocolVersion int    `json:"protocolVersion"`
	SimVersion      string `json:"simVersion"`
	Rooms           int    `json:"rooms"`
	ResumeSessions  int    `json:"resumeSessions"`
}

type adminStatsResponse struct {
	ProtocolVersion int                 `json:"protocolVersion"`
	SimVersion      string              `json:"simVersion"`
	BuildHash       string              `json:"buildHash,omitempty"`
	TickIntervalMs  int64               `json:"tickIntervalMs"`
	Rooms           []roomRuntimeStats  `json:"rooms"`
	ResumeSessions  []resumeSessionStat `json:"resumeSessions"`
}

type resumeSessionStat struct {
	Token       string `json:"token"`
	ClientID    string `json:"clientId"`
	ClientName  string `json:"clientName"`
	RoomID      string `json:"roomId"`
	ExpiresInMs int64  `json:"expiresInMs"`
}

type handshakeError struct {
	closeCode int
	reason    string
}

func (e *handshakeError) Error() string { return e.reason }

func writeHandshakeClose(conn *websocket.Conn, err error) {
	var hsErr *handshakeError
	if !errors.As(err, &hsErr) {
		return
	}
	_ = conn.WriteControl(
		websocket.CloseMessage,
		websocket.FormatCloseMessage(hsErr.closeCode, hsErr.reason),
		time.Now().Add(250*time.Millisecond),
	)
}

func writeProtocolClose(conn *websocket.Conn, closeCode int, reason string) {
	_ = conn.WriteControl(
		websocket.CloseMessage,
		websocket.FormatCloseMessage(closeCode, reason),
		time.Now().Add(250*time.Millisecond),
	)
}

var safeTokenPattern = regexp.MustCompile(`^[A-Za-z0-9._:-]+$`)

func NewServer(cfg Config) *Server {
	if cfg.TickInterval <= 0 {
		cfg.TickInterval = 20 * time.Millisecond
	}
	if cfg.SimVersion == "" {
		cfg.SimVersion = "dev"
	}
	if cfg.MaxReadBytes <= 0 {
		cfg.MaxReadBytes = 64 * 1024
	}
	if cfg.MaxBatchCommands <= 0 {
		cfg.MaxBatchCommands = 64
	}
	if cfg.MaxRequestIDLength <= 0 {
		cfg.MaxRequestIDLength = 64
	}
	if cfg.MaxUnitIDsPerCommand <= 0 {
		cfg.MaxUnitIDsPerCommand = 64
	}
	if cfg.MaxClientNameLength <= 0 {
		cfg.MaxClientNameLength = 32
	}
	if cfg.MaxRoomIDLength <= 0 {
		cfg.MaxRoomIDLength = 32
	}
	if cfg.MaxPendingBatchesPerClient <= 0 {
		cfg.MaxPendingBatchesPerClient = 8
	}
	if cfg.MaxOutboundQueue <= 0 {
		cfg.MaxOutboundQueue = 128
	}
	if cfg.EmptyRoomTTL <= 0 {
		cfg.EmptyRoomTTL = 30 * time.Second
	}
	if cfg.ResumeWindow <= 0 {
		cfg.ResumeWindow = 15 * time.Second
	}
	s := &Server{
		cfg:            cfg,
		upgrader:       websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }},
		rooms:          make(map[string]*room),
		roomMatchEnded: make(map[string]bool),
		tickSamples:    make([]int64, 256),
		resumeSessions: make(map[string]resumeSession),
	}
	if cfg.ReplayPath != "" {
		replay, err := NewReplayWriter(cfg.ReplayPath)
		if err == nil {
			s.replay = replay
			_ = s.replay.WriteHeader(cfg.SimVersion, cfg.BuildHash, protocol.CurrentProtocolVersion, int(cfg.TickInterval/time.Millisecond))
		} else {
			log.Printf("replay disabled: %v", err)
		}
	}
	return s
}

func (s *Server) Run(ctx context.Context) error {
	ln, err := net.Listen("tcp", s.cfg.Addr)
	if err != nil {
		return err
	}
	return s.RunOnListener(ctx, ln)
}

func (s *Server) RunOnListener(ctx context.Context, ln net.Listener) error {
	s.httpSrv = &http.Server{Addr: ln.Addr().String(), Handler: s.Handler()}
	defer func() {
		if s.replay != nil {
			_ = s.replay.Close()
		}
	}()

	ticker := time.NewTicker(s.cfg.TickInterval)
	defer ticker.Stop()
	go func() {
		for {
			select {
			case <-ticker.C:
				s.stepRooms()
			case <-ctx.Done():
				return
			}
		}
	}()

	errCh := make(chan error, 1)
	go func() {
		if err := s.httpSrv.Serve(ln); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
		close(errCh)
	}()

	select {
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = s.httpSrv.Shutdown(shutdownCtx)
		return nil
	case err := <-errCh:
		return err
	}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/ws", s.handleWS)
	mux.HandleFunc("/healthz", s.handleHealth)
	mux.HandleFunc("/admin/stats", s.handleAdminStats)
	return mux
}

func (s *Server) Close() error {
	if s.replay != nil {
		err := s.replay.Close()
		s.replay = nil
		return err
	}
	return nil
}

func (s *Server) handleWS(w http.ResponseWriter, r *http.Request) {
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	conn.SetReadLimit(s.cfg.MaxReadBytes)

	client, room, err := s.handshake(conn)
	if err != nil {
		writeHandshakeClose(conn, err)
		_ = conn.Close()
		return
	}
	room.addClient(client)
	defer func() {
		room.removeClient(client)
		s.rememberResumeSession(client)
		_ = conn.Close()
	}()

	resumeToken := client.resumeToken
	_ = client.sendEnvelope(protocol.HandshakeAckMessage{
		Type: "handshakeAck", RoomID: room.id, ClientID: client.id,
		ServerTickMs: int(s.cfg.TickInterval / time.Millisecond), ProtocolVersion: protocol.CurrentProtocolVersion,
		ResumeToken: &resumeToken,
	})
	snap := room.snapshot()
	_ = client.sendEnvelope(protocol.SnapshotMessage{
		Type: "snapshot", Tick: snap.Tick, WorldHash: snap.WorldHash, Units: snap.Units,
		MatchEnded: snap.MatchEnded, WinnerID: snap.WinnerID,
	})

	for {
		var env protocol.ProtocolEnvelope
		if err := conn.ReadJSON(&env); err != nil {
			return
		}
		if env.ProtocolVersion != protocol.CurrentProtocolVersion {
			return
		}
		if strings.TrimSpace(env.SimVersion) == "" {
			writeProtocolClose(conn, websocket.ClosePolicyViolation, "invalid sim version")
			return
		}
		t, err := protocol.DecodeMessageType(env.Message)
		if err != nil {
			return
		}
		if t == "commandBatch" {
			var batch protocol.CommandBatchMessage
			if err := json.Unmarshal(env.Message, &batch); err != nil {
				return
			}
			if len(batch.Commands) > s.cfg.MaxBatchCommands {
				_ = client.sendEnvelope(protocol.CommandAckMessage{
					Type: "commandAck", Tick: batch.Tick, CommandType: "commandBatch",
					Accepted: false, Reason: "batchTooLarge",
				})
				continue
			}
			if !s.validateCommandBatch(batch) {
				_ = client.sendEnvelope(protocol.CommandAckMessage{
					Type: "commandAck", Tick: batch.Tick, CommandType: "commandBatch",
					Accepted: false, Reason: "invalidPayload",
				})
				continue
			}
			if !room.enqueue(client.id, batch) {
				_ = client.sendEnvelope(protocol.CommandAckMessage{
					Type: "commandAck", Tick: batch.Tick, CommandType: "commandBatch",
					Accepted: false, Reason: "queueFull",
				})
				continue
			}
		}
	}
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pruneExpiredResumeSessionsLocked()
	resp := healthResponse{
		Status:          "ok",
		ProtocolVersion: protocol.CurrentProtocolVersion,
		SimVersion:      s.cfg.SimVersion,
		Rooms:           len(s.rooms),
		ResumeSessions:  len(s.resumeSessions),
	}
	writeJSON(w, resp)
}

func (s *Server) handleAdminStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	now := time.Now()
	s.mu.Lock()
	s.pruneExpiredResumeSessionsLocked()
	rooms := make([]*room, 0, len(s.rooms))
	for _, rm := range s.rooms {
		rooms = append(rooms, rm)
	}
	resumeStats := make([]resumeSessionStat, 0, len(s.resumeSessions))
	for token, session := range s.resumeSessions {
		resumeStats = append(resumeStats, resumeSessionStat{
			Token:       token,
			ClientID:    session.clientID,
			ClientName:  session.name,
			RoomID:      session.roomID,
			ExpiresInMs: session.expires.Sub(now).Milliseconds(),
		})
	}
	cfg := s.cfg
	s.mu.Unlock()

	sort.Slice(resumeStats, func(i, j int) bool { return resumeStats[i].Token < resumeStats[j].Token })
	roomStats := make([]roomRuntimeStats, 0, len(rooms))
	for _, rm := range rooms {
		roomStats = append(roomStats, rm.runtimeStats(now))
	}
	sort.Slice(roomStats, func(i, j int) bool { return roomStats[i].ID < roomStats[j].ID })

	resp := adminStatsResponse{
		ProtocolVersion: protocol.CurrentProtocolVersion,
		SimVersion:      cfg.SimVersion,
		BuildHash:       cfg.BuildHash,
		TickIntervalMs:  cfg.TickInterval.Milliseconds(),
		Rooms:           roomStats,
		ResumeSessions:  resumeStats,
	}
	writeJSON(w, resp)
}

func (s *Server) handshake(conn *websocket.Conn) (*clientConn, *room, error) {
	var env protocol.ProtocolEnvelope
	if err := conn.ReadJSON(&env); err != nil {
		return nil, nil, err
	}
	if env.ProtocolVersion != protocol.CurrentProtocolVersion {
		reason := "protocol mismatch"
		switch protocol.Compatibility(protocol.CurrentProtocolVersion, env.ProtocolVersion) {
		case protocol.UpgradeClient:
			reason = "protocol mismatch: upgrade client"
		case protocol.UpgradeServer:
			reason = "protocol mismatch: upgrade server"
		}
		return nil, nil, &handshakeError{closeCode: websocket.ClosePolicyViolation, reason: reason}
	}
	if strings.TrimSpace(env.SimVersion) == "" {
		return nil, nil, &handshakeError{closeCode: websocket.ClosePolicyViolation, reason: "invalid sim version"}
	}
	msgType, err := protocol.DecodeMessageType(env.Message)
	if err != nil || msgType != "handshake" {
		return nil, nil, &handshakeError{closeCode: websocket.CloseUnsupportedData, reason: "invalid handshake"}
	}
	var hs protocol.HandshakeMessage
	if err := json.Unmarshal(env.Message, &hs); err != nil {
		return nil, nil, &handshakeError{closeCode: websocket.CloseUnsupportedData, reason: "invalid handshake"}
	}
	if hs.ClientName == "" {
		return nil, nil, &handshakeError{closeCode: websocket.ClosePolicyViolation, reason: "empty client name"}
	}
	if len(hs.ClientName) > s.cfg.MaxClientNameLength || !safeTokenPattern.MatchString(hs.ClientName) {
		return nil, nil, &handshakeError{closeCode: websocket.ClosePolicyViolation, reason: "invalid client name"}
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	s.pruneExpiredResumeSessionsLocked()
	clientID := ""
	roomID := ""
	clientName := hs.ClientName
	if hs.ResumeToken != nil && *hs.ResumeToken != "" {
		session, ok := s.resumeSessions[*hs.ResumeToken]
		if !ok || time.Now().After(session.expires) {
			return nil, nil, &handshakeError{closeCode: websocket.ClosePolicyViolation, reason: "invalid resume token"}
		}
		delete(s.resumeSessions, *hs.ResumeToken)
		clientID = session.clientID
		roomID = session.roomID
		clientName = session.name
	} else {
		s.nextID++
		clientID = fmt.Sprintf("player-%d", s.nextID)
		roomID = "default"
		if hs.RequestedRoom != nil && *hs.RequestedRoom != "" {
			roomID = *hs.RequestedRoom
		}
	}
	if len(roomID) > s.cfg.MaxRoomIDLength || !safeTokenPattern.MatchString(roomID) {
		return nil, nil, &handshakeError{closeCode: websocket.ClosePolicyViolation, reason: "invalid room id"}
	}
	rm, ok := s.rooms[roomID]
	if !ok {
		rm = newRoom(roomID)
		rm.maxPendingBatchesPerClient = s.cfg.MaxPendingBatchesPerClient
		s.rooms[roomID] = rm
	}
	resumeToken := s.issueResumeTokenLocked(clientID, clientName, roomID)
	return &clientConn{
		id: clientID, name: clientName, roomID: roomID, conn: conn,
		simVersion: s.cfg.SimVersion, buildHash: s.cfg.BuildHash,
		resumeToken: resumeToken,
	}, rm, nil
}

func (s *Server) validateCommandBatch(batch protocol.CommandBatchMessage) bool {
	for _, cmd := range batch.Commands {
		if cmd.CommandType == "" || !safeTokenPattern.MatchString(cmd.CommandType) {
			return false
		}
		if cmd.RequestID != nil {
			if len(*cmd.RequestID) > s.cfg.MaxRequestIDLength || !safeTokenPattern.MatchString(*cmd.RequestID) {
				return false
			}
		}
		if len(cmd.UnitIDs) > s.cfg.MaxUnitIDsPerCommand {
			return false
		}
		if cmd.X != nil && !isFinite(*cmd.X) {
			return false
		}
		if cmd.Y != nil && !isFinite(*cmd.Y) {
			return false
		}
		switch cmd.CommandType {
		case "move":
			if len(cmd.UnitIDs) == 0 || cmd.X == nil || cmd.Y == nil {
				return false
			}
		case "attack":
			if len(cmd.UnitIDs) == 0 || cmd.TargetUnitID == nil {
				return false
			}
		case "build":
			if cmd.X == nil || cmd.Y == nil {
				return false
			}
		case "queue":
			// no-op: optional UnitType only
		case "surrender":
			if len(cmd.UnitIDs) > 0 || cmd.X != nil || cmd.Y != nil || cmd.TargetUnitID != nil || cmd.UnitType != nil {
				return false
			}
		default:
			return false
		}
	}
	return true
}

func isFinite(v float64) bool {
	return !math.IsNaN(v) && !math.IsInf(v, 0)
}

func (s *Server) stepRooms() {
	s.mu.Lock()
	rooms := make([]*room, 0, len(s.rooms))
	for _, rm := range s.rooms {
		rooms = append(rooms, rm)
	}
	s.mu.Unlock()

	for _, rm := range rooms {
		pendingBefore := rm.pendingBatchCount()
		start := time.Now()
		result := rm.step()
		s.recordTickSample(time.Since(start).Nanoseconds(), pendingBefore)
		msg := protocol.SnapshotMessage{
			Type: "snapshot", Tick: result.snapshot.Tick, WorldHash: result.snapshot.WorldHash, Units: result.snapshot.Units,
			MatchEnded: result.snapshot.MatchEnded, WinnerID: result.snapshot.WinnerID,
		}
		rm.mu.Lock()
		clients := make([]*clientConn, 0, len(rm.clients))
		byID := make(map[string]*clientConn, len(rm.clients))
		for c := range rm.clients {
			clients = append(clients, c)
			byID[c.id] = c
		}
		rm.mu.Unlock()
		for _, ack := range result.acks {
			c := byID[ack.clientID]
			if c == nil {
				continue
			}
			if s.replay != nil {
				_ = s.replay.WriteCommand(ack.clientID, ack.command, ack.ack)
			}
			if err := c.sendEnvelope(ack.ack); err != nil {
				log.Printf("command ack send failed: %v", err)
			}
		}
		if s.replay != nil && s.cfg.KeyframeEvery > 0 && result.snapshot.Tick%s.cfg.KeyframeEvery == 0 {
			_ = s.replay.WriteKeyframe(result.snapshot.Tick, result.snapshot.WorldHash, result.snapshot.Units)
		}
		justEnded := result.matchEnded && !s.roomMatchEnded[rm.id]
		for _, c := range clients {
			if err := c.sendEnvelope(msg); err != nil {
				log.Printf("snapshot send failed: %v", err)
			}
		}
		if justEnded {
			if s.replay != nil {
				_ = s.replay.WriteMatchEnd(result.snapshot.Tick, result.snapshot.WinnerID)
			}
			end := protocol.MatchEndMessage{Type: "matchEnd", Tick: result.snapshot.Tick, WinnerID: result.snapshot.WinnerID}
			for _, c := range clients {
				if err := c.sendEnvelope(end); err != nil {
					log.Printf("matchEnd send failed: %v", err)
				}
			}
			s.roomMatchEnded[rm.id] = true
		}
		if result.snapshot.Tick%200 == 0 {
			p50, p95, p99 := s.tickPercentiles()
			log.Printf(
				"tick-metrics room=%s tick=%d p50us=%d p95us=%d p99us=%d maxPending=%d",
				rm.id,
				result.snapshot.Tick,
				p50/1000,
				p95/1000,
				p99/1000,
				s.maxPending(),
			)
		}
		if rm.shouldEvict(time.Now(), s.cfg.EmptyRoomTTL) {
			s.mu.Lock()
			delete(s.rooms, rm.id)
			delete(s.roomMatchEnded, rm.id)
			s.pruneExpiredResumeSessionsLocked()
			s.mu.Unlock()
		}
	}
}

func (s *Server) issueResumeTokenLocked(clientID, name, roomID string) string {
	s.nextResumeID++
	token := fmt.Sprintf("resume-%d", s.nextResumeID)
	s.resumeSessions[token] = resumeSession{
		clientID: clientID,
		name:     name,
		roomID:   roomID,
		expires:  time.Now().Add(s.cfg.ResumeWindow),
	}
	return token
}

func (s *Server) rememberResumeSession(client *clientConn) {
	if client == nil || client.resumeToken == "" {
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	session, ok := s.resumeSessions[client.resumeToken]
	if !ok {
		return
	}
	session.expires = time.Now().Add(s.cfg.ResumeWindow)
	s.resumeSessions[client.resumeToken] = session
}

func (s *Server) pruneExpiredResumeSessionsLocked() {
	now := time.Now()
	for token, session := range s.resumeSessions {
		if now.After(session.expires) {
			delete(s.resumeSessions, token)
		}
	}
}

func (c *clientConn) sendEnvelope(msg any) (err error) {
	raw, err := json.Marshal(msg)
	if err != nil {
		return err
	}
	env := protocol.ProtocolEnvelope{
		ProtocolVersion: protocol.CurrentProtocolVersion,
		SimVersion:      c.simVersion,
		Message:         raw,
	}
	if c.buildHash != "" {
		env.BuildHash = &c.buildHash
	}
	if !c.writeMu.TryLock() {
		return fmt.Errorf("connection busy")
	}
	defer c.writeMu.Unlock()
	if c.conn == nil {
		return fmt.Errorf("connection unavailable")
	}
	_ = c.conn.SetWriteDeadline(time.Now().Add(1500 * time.Millisecond))
	defer func() {
		_ = c.conn.SetWriteDeadline(time.Time{})
	}()
	return c.conn.WriteJSON(env)
}

func (s *Server) recordTickSample(ns int64, pending int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if pending > s.maxPendingSeen {
		s.maxPendingSeen = pending
	}
	s.tickSamples[s.tickCursor] = ns
	s.tickCursor = (s.tickCursor + 1) % len(s.tickSamples)
	if s.tickCount < len(s.tickSamples) {
		s.tickCount++
	}
}

func (s *Server) tickPercentiles() (int64, int64, int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.tickCount == 0 {
		return 0, 0, 0
	}
	data := make([]int64, s.tickCount)
	copy(data, s.tickSamples[:s.tickCount])
	sort.Slice(data, func(i, j int) bool { return data[i] < data[j] })
	return percentileNs(data, 50), percentileNs(data, 95), percentileNs(data, 99)
}

func (s *Server) maxPending() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.maxPendingSeen
}

func percentileNs(sorted []int64, p int) int64 {
	if len(sorted) == 0 {
		return 0
	}
	if p <= 0 {
		return sorted[0]
	}
	if p >= 100 {
		return sorted[len(sorted)-1]
	}
	index := (p * (len(sorted) - 1)) / 100
	return sorted[index]
}

func writeJSON(w http.ResponseWriter, payload any) {
	w.Header().Set("Content-Type", "application/json")
	encoder := json.NewEncoder(w)
	_ = encoder.Encode(payload)
}
