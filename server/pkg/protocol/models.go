package protocol

import "encoding/json"

const CurrentProtocolVersion = 1

type ProtocolEnvelope struct {
	ProtocolVersion int             `json:"protocolVersion"`
	SimVersion      string          `json:"simVersion"`
	BuildHash       *string         `json:"buildHash,omitempty"`
	Message         json.RawMessage `json:"message"`
}

// HandshakeMessage is used for initial client-server capability exchange.
type HandshakeMessage struct {
	Type          string  `json:"type"`
	ClientName    string  `json:"clientName"`
	RequestedRoom *string `json:"requestedRoom,omitempty"`
}

type HandshakeAckMessage struct {
	Type            string `json:"type"`
	RoomID          string `json:"roomId"`
	ClientID        string `json:"clientId"`
	ServerTickMs    int    `json:"serverTickMs"`
	ProtocolVersion int    `json:"protocolVersion"`
}

// WireCommand is an opaque command shell that carries type/request id.
type WireCommand struct {
	CommandType  string   `json:"commandType"`
	RequestID    *string  `json:"requestId,omitempty"`
	UnitIDs      []int    `json:"unitIds,omitempty"`
	X            *float64 `json:"x,omitempty"`
	Y            *float64 `json:"y,omitempty"`
	TargetUnitID *int     `json:"targetUnitId,omitempty"`
	UnitType     *string  `json:"unitType,omitempty"`
}

type CommandBatchMessage struct {
	Type     string        `json:"type"`
	Tick     int           `json:"tick"`
	Commands []WireCommand `json:"commands"`
}

type CommandAckMessage struct {
	Type        string  `json:"type"`
	Tick        int     `json:"tick"`
	RequestID   *string `json:"requestId,omitempty"`
	CommandType string  `json:"commandType"`
	Accepted    bool    `json:"accepted"`
	Reason      string  `json:"reason,omitempty"`
}

type SnapshotMessage struct {
	Type       string         `json:"type"`
	Tick       int            `json:"tick"`
	WorldHash  int64          `json:"worldHash"`
	Units      []SnapshotUnit `json:"units,omitempty"`
	MatchEnded bool           `json:"matchEnded,omitempty"`
	WinnerID   *string        `json:"winnerId,omitempty"`
}

type MatchEndMessage struct {
	Type     string  `json:"type"`
	Tick     int     `json:"tick"`
	WinnerID *string `json:"winnerId,omitempty"`
}

type SnapshotUnit struct {
	ID      int     `json:"id"`
	OwnerID string  `json:"ownerId"`
	TypeID  string  `json:"typeId"`
	X       float64 `json:"x"`
	Y       float64 `json:"y"`
	HP      int     `json:"hp"`
}

type ProtocolCompatibility int

const (
	Compatible ProtocolCompatibility = iota
	UpgradeServer
	UpgradeClient
)

func Compatibility(localVersion, remoteVersion int) ProtocolCompatibility {
	switch {
	case remoteVersion == localVersion:
		return Compatible
	case remoteVersion > localVersion:
		return UpgradeClient
	default:
		return UpgradeServer
	}
}
