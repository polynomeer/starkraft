package authoritative

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/starkraft/server/pkg/protocol"
)

type Config struct {
	Addr        string
	SimVersion  string
	BuildHash   string
	TickInterval time.Duration
	ReplayPath string
	KeyframeEvery int
}

type Server struct {
	cfg      Config
	upgrader websocket.Upgrader
	rooms    map[string]*room
	nextID   int
	mu       sync.Mutex
	httpSrv  *http.Server
	replay   *ReplayWriter
}

type clientConn struct {
	id       string
	name     string
	roomID   string
	simVersion string
	buildHash string
	conn     *websocket.Conn
	writeMu  sync.Mutex
}

func NewServer(cfg Config) *Server {
	if cfg.TickInterval <= 0 {
		cfg.TickInterval = 20 * time.Millisecond
	}
	if cfg.SimVersion == "" {
		cfg.SimVersion = "dev"
	}
	s := &Server{
		cfg: cfg,
		upgrader: websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }},
		rooms: make(map[string]*room),
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
	s.httpSrv = &http.Server{Addr: s.cfg.Addr, Handler: s.Handler()}
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
		if err := s.httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
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

	client, room, err := s.handshake(conn)
	if err != nil {
		_ = conn.Close()
		return
	}
	room.addClient(client)
	defer func() {
		room.removeClient(client)
		_ = conn.Close()
	}()

	_ = client.sendEnvelope(protocol.HandshakeAckMessage{
		Type: "handshakeAck", RoomID: room.id, ClientID: client.id,
		ServerTickMs: int(s.cfg.TickInterval / time.Millisecond), ProtocolVersion: protocol.CurrentProtocolVersion,
	})
	snap := room.snapshot()
	_ = client.sendEnvelope(protocol.SnapshotMessage{Type: "snapshot", Tick: snap.Tick, WorldHash: snap.WorldHash, Units: snap.Units})

	for {
		var env protocol.ProtocolEnvelope
		if err := conn.ReadJSON(&env); err != nil {
			return
		}
		if env.ProtocolVersion != protocol.CurrentProtocolVersion {
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
			room.enqueue(client.id, batch)
		}
	}
}

func (s *Server) handshake(conn *websocket.Conn) (*clientConn, *room, error) {
	var env protocol.ProtocolEnvelope
	if err := conn.ReadJSON(&env); err != nil {
		return nil, nil, err
	}
	if env.ProtocolVersion != protocol.CurrentProtocolVersion {
		return nil, nil, fmt.Errorf("protocol mismatch")
	}
	msgType, err := protocol.DecodeMessageType(env.Message)
	if err != nil || msgType != "handshake" {
		return nil, nil, fmt.Errorf("invalid handshake")
	}
	var hs protocol.HandshakeMessage
	if err := json.Unmarshal(env.Message, &hs); err != nil {
		return nil, nil, err
	}
	if hs.ClientName == "" {
		return nil, nil, fmt.Errorf("empty client name")
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	s.nextID++
	clientID := fmt.Sprintf("player-%d", s.nextID)
	roomID := "default"
	if hs.RequestedRoom != nil && *hs.RequestedRoom != "" {
		roomID = *hs.RequestedRoom
	}
	rm, ok := s.rooms[roomID]
	if !ok {
		rm = newRoom(roomID)
		s.rooms[roomID] = rm
	}
	return &clientConn{
		id: clientID, name: hs.ClientName, roomID: roomID, conn: conn,
		simVersion: s.cfg.SimVersion, buildHash: s.cfg.BuildHash,
	}, rm, nil
}

func (s *Server) stepRooms() {
	s.mu.Lock()
	rooms := make([]*room, 0, len(s.rooms))
	for _, rm := range s.rooms {
		rooms = append(rooms, rm)
	}
	s.mu.Unlock()

	for _, rm := range rooms {
		result := rm.step()
		msg := protocol.SnapshotMessage{Type: "snapshot", Tick: result.snapshot.Tick, WorldHash: result.snapshot.WorldHash, Units: result.snapshot.Units}
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
		for _, c := range clients {
			if err := c.sendEnvelope(msg); err != nil {
				log.Printf("snapshot send failed: %v", err)
			}
		}
	}
}

func (c *clientConn) sendEnvelope(msg any) error {
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
	c.writeMu.Lock()
	defer c.writeMu.Unlock()
	return c.conn.WriteJSON(env)
}
