package authoritative

import (
	"bufio"
	"encoding/json"
	"os"
	"sync"
	"time"

	"github.com/starkraft/server/pkg/protocol"
)

type ReplayWriter struct {
	mu sync.Mutex
	f  *os.File
	w  *bufio.Writer
}

type replayHeaderRecord struct {
	RecordType      string `json:"recordType"`
	TimestampUTC    string `json:"timestampUtc"`
	ProtocolVersion int    `json:"protocolVersion"`
	SimVersion      string `json:"simVersion"`
	BuildHash       string `json:"buildHash,omitempty"`
	TickMs          int    `json:"tickMs"`
}

type replayCommandRecord struct {
	RecordType string                `json:"recordType"`
	ClientID   string                `json:"clientId"`
	Command    protocol.WireCommand  `json:"command"`
	Ack        protocol.CommandAckMessage `json:"ack"`
}

type replayKeyframeRecord struct {
	RecordType string                  `json:"recordType"`
	Tick       int                     `json:"tick"`
	WorldHash  int64                   `json:"worldHash"`
	Units      []protocol.SnapshotUnit `json:"units"`
}

type replayMatchEndRecord struct {
	RecordType string  `json:"recordType"`
	Tick       int     `json:"tick"`
	WinnerID   *string `json:"winnerId,omitempty"`
}

func NewReplayWriter(path string) (*ReplayWriter, error) {
	f, err := os.Create(path)
	if err != nil {
		return nil, err
	}
	return &ReplayWriter{f: f, w: bufio.NewWriterSize(f, 64*1024)}, nil
}

func (r *ReplayWriter) WriteHeader(simVersion, buildHash string, protocolVersion, tickMs int) error {
	return r.write(replayHeaderRecord{
		RecordType:      "header",
		TimestampUTC:    time.Now().UTC().Format(time.RFC3339Nano),
		ProtocolVersion: protocolVersion,
		SimVersion:      simVersion,
		BuildHash:       buildHash,
		TickMs:          tickMs,
	})
}

func (r *ReplayWriter) WriteCommand(clientID string, cmd protocol.WireCommand, ack protocol.CommandAckMessage) error {
	return r.write(replayCommandRecord{RecordType: "command", ClientID: clientID, Command: cmd, Ack: ack})
}

func (r *ReplayWriter) WriteKeyframe(tick int, worldHash int64, units []protocol.SnapshotUnit) error {
	copyUnits := make([]protocol.SnapshotUnit, len(units))
	copy(copyUnits, units)
	return r.write(replayKeyframeRecord{RecordType: "keyframe", Tick: tick, WorldHash: worldHash, Units: copyUnits})
}

func (r *ReplayWriter) WriteMatchEnd(tick int, winnerID *string) error {
	return r.write(replayMatchEndRecord{RecordType: "matchEnd", Tick: tick, WinnerID: winnerID})
}

func (r *ReplayWriter) write(v any) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	if _, err := r.w.Write(b); err != nil {
		return err
	}
	if err := r.w.WriteByte('\n'); err != nil {
		return err
	}
	return r.w.Flush()
}

func (r *ReplayWriter) Close() error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.f == nil {
		return nil
	}
	if r.w != nil {
		if err := r.w.Flush(); err != nil {
			_ = r.f.Close()
			r.f = nil
			r.w = nil
			return err
		}
	}
	err := r.f.Close()
	r.f = nil
	r.w = nil
	return err
}
