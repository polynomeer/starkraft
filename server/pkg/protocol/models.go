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

// WireCommand is an opaque command shell that carries type/request id.
type WireCommand struct {
	CommandType string  `json:"commandType"`
	RequestID   *string `json:"requestId,omitempty"`
}

type CommandBatchMessage struct {
	Type     string        `json:"type"`
	Tick     int           `json:"tick"`
	Commands []WireCommand `json:"commands"`
}

type SnapshotMessage struct {
	Type      string `json:"type"`
	Tick      int    `json:"tick"`
	WorldHash int64  `json:"worldHash"`
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
