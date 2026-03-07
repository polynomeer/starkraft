package headless

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/starkraft/client/pkg/protocol"
)

type SnapshotBuffer struct {
	prev *protocol.SnapshotMessage
	last *protocol.SnapshotMessage
}

type InterpolatedUnit struct {
	ID      int
	OwnerID string
	TypeID  string
	X       float64
	Y       float64
	HP      int
}

func (b *SnapshotBuffer) Push(s protocol.SnapshotMessage) {
	if b.last != nil {
		p := *b.last
		p.Units = append([]protocol.SnapshotUnit(nil), b.last.Units...)
		b.prev = &p
	}
	copyS := s
	copyS.Units = append([]protocol.SnapshotUnit(nil), s.Units...)
	b.last = &copyS
}

func (b *SnapshotBuffer) Interpolate(alpha float64) []InterpolatedUnit {
	if b.last == nil {
		return nil
	}
	if b.prev == nil {
		out := make([]InterpolatedUnit, 0, len(b.last.Units))
		for _, u := range b.last.Units {
			out = append(out, InterpolatedUnit{ID: u.ID, OwnerID: u.OwnerID, TypeID: u.TypeID, X: u.X, Y: u.Y, HP: u.HP})
		}
		return out
	}
	if alpha < 0 {
		alpha = 0
	}
	if alpha > 1 {
		alpha = 1
	}
	prevByID := make(map[int]protocol.SnapshotUnit, len(b.prev.Units))
	for _, u := range b.prev.Units {
		prevByID[u.ID] = u
	}
	out := make([]InterpolatedUnit, 0, len(b.last.Units))
	for _, u := range b.last.Units {
		p, ok := prevByID[u.ID]
		if !ok {
			out = append(out, InterpolatedUnit{ID: u.ID, OwnerID: u.OwnerID, TypeID: u.TypeID, X: u.X, Y: u.Y, HP: u.HP})
			continue
		}
		x := p.X + (u.X-p.X)*alpha
		y := p.Y + (u.Y-p.Y)*alpha
		out = append(out, InterpolatedUnit{ID: u.ID, OwnerID: u.OwnerID, TypeID: u.TypeID, X: x, Y: y, HP: u.HP})
	}
	return out
}

type Client struct {
	conn        *websocket.Conn
	simVersion  string
	name        string
	room        *string
	clientID    string
	resumeToken *string

	SnapshotCh chan protocol.SnapshotMessage
	MatchEndCh chan protocol.MatchEndMessage
	AckCh      chan protocol.CommandAckMessage
	ErrCh      chan error

	mu sync.Mutex
}

func Dial(url, simVersion, name string, room *string) (*Client, error) {
	conn, _, err := websocket.DefaultDialer.Dial(url, nil)
	if err != nil {
		return nil, err
	}
	c := &Client{
		conn:       conn,
		simVersion: simVersion,
		name:       name,
		room:       room,
		SnapshotCh: make(chan protocol.SnapshotMessage, 64),
		MatchEndCh: make(chan protocol.MatchEndMessage, 8),
		AckCh:      make(chan protocol.CommandAckMessage, 128),
		ErrCh:      make(chan error, 1),
	}
	if err := c.handshake(); err != nil {
		_ = conn.Close()
		return nil, err
	}
	go c.readLoop()
	return c, nil
}

func (c *Client) Close() error {
	return c.conn.Close()
}

func (c *Client) ClientID() string { return c.clientID }

func (c *Client) SendBatch(tick int, commands []protocol.WireCommand) error {
	if len(commands) == 0 {
		return nil
	}
	raw, err := json.Marshal(protocol.CommandBatchMessage{Type: "commandBatch", Tick: tick, Commands: commands})
	if err != nil {
		return err
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.conn.WriteJSON(protocol.ProtocolEnvelope{
		ProtocolVersion: protocol.CurrentProtocolVersion,
		SimVersion:      c.simVersion,
		Message:         raw,
	})
}

func (c *Client) handshake() error {
	raw, err := json.Marshal(protocol.HandshakeMessage{Type: "handshake", ClientName: c.name, RequestedRoom: c.room})
	if err != nil {
		return err
	}
	if err := c.conn.WriteJSON(protocol.ProtocolEnvelope{ProtocolVersion: protocol.CurrentProtocolVersion, SimVersion: c.simVersion, Message: raw}); err != nil {
		return err
	}
	_ = c.conn.SetReadDeadline(time.Now().Add(3 * time.Second))
	var env protocol.ProtocolEnvelope
	if err := c.conn.ReadJSON(&env); err != nil {
		return err
	}
	_ = c.conn.SetReadDeadline(time.Time{})
	switch protocol.Compatibility(protocol.CurrentProtocolVersion, env.ProtocolVersion) {
	case protocol.Compatible:
		// continue
	case protocol.UpgradeClient:
		return fmt.Errorf("protocol mismatch: local=%d remote=%d (upgrade client)", protocol.CurrentProtocolVersion, env.ProtocolVersion)
	case protocol.UpgradeServer:
		return fmt.Errorf("protocol mismatch: local=%d remote=%d (upgrade server)", protocol.CurrentProtocolVersion, env.ProtocolVersion)
	default:
		return fmt.Errorf("protocol mismatch: local=%d remote=%d", protocol.CurrentProtocolVersion, env.ProtocolVersion)
	}
	mt, err := protocol.DecodeMessageType(env.Message)
	if err != nil {
		return err
	}
	if mt != "handshakeAck" {
		return fmt.Errorf("expected handshakeAck, got %s", mt)
	}
	var ack protocol.HandshakeAckMessage
	if err := json.Unmarshal(env.Message, &ack); err != nil {
		return err
	}
	switch protocol.Compatibility(protocol.CurrentProtocolVersion, ack.ProtocolVersion) {
	case protocol.Compatible:
		// continue
	case protocol.UpgradeClient:
		return fmt.Errorf("protocol mismatch: local=%d remote=%d (upgrade client)", protocol.CurrentProtocolVersion, ack.ProtocolVersion)
	case protocol.UpgradeServer:
		return fmt.Errorf("protocol mismatch: local=%d remote=%d (upgrade server)", protocol.CurrentProtocolVersion, ack.ProtocolVersion)
	default:
		return fmt.Errorf("protocol mismatch: local=%d remote=%d", protocol.CurrentProtocolVersion, ack.ProtocolVersion)
	}
	c.clientID = ack.ClientID
	c.resumeToken = ack.ResumeToken
	return nil
}

func (c *Client) readLoop() {
	for {
		var env protocol.ProtocolEnvelope
		if err := c.conn.ReadJSON(&env); err != nil {
			c.ErrCh <- err
			return
		}
		switch protocol.Compatibility(protocol.CurrentProtocolVersion, env.ProtocolVersion) {
		case protocol.Compatible:
			// continue
		case protocol.UpgradeClient:
			c.ErrCh <- fmt.Errorf("protocol mismatch: local=%d remote=%d (upgrade client)", protocol.CurrentProtocolVersion, env.ProtocolVersion)
			return
		case protocol.UpgradeServer:
			c.ErrCh <- fmt.Errorf("protocol mismatch: local=%d remote=%d (upgrade server)", protocol.CurrentProtocolVersion, env.ProtocolVersion)
			return
		default:
			c.ErrCh <- fmt.Errorf("protocol mismatch: local=%d remote=%d", protocol.CurrentProtocolVersion, env.ProtocolVersion)
			return
		}
		mt, err := protocol.DecodeMessageType(env.Message)
		if err != nil {
			c.ErrCh <- err
			return
		}
		switch mt {
		case "snapshot":
			var s protocol.SnapshotMessage
			if err := json.Unmarshal(env.Message, &s); err != nil {
				c.ErrCh <- err
				return
			}
			c.SnapshotCh <- s
		case "commandAck":
			var ack protocol.CommandAckMessage
			if err := json.Unmarshal(env.Message, &ack); err != nil {
				c.ErrCh <- err
				return
			}
			c.AckCh <- ack
		case "matchEnd":
			var end protocol.MatchEndMessage
			if err := json.Unmarshal(env.Message, &end); err != nil {
				c.ErrCh <- err
				return
			}
			c.MatchEndCh <- end
		}
	}
}
