package protocol

import "encoding/json"

type MessageType struct {
	Type string `json:"type"`
}

func DecodeMessageType(raw json.RawMessage) (string, error) {
	var mt MessageType
	if err := json.Unmarshal(raw, &mt); err != nil {
		return "", err
	}
	return mt.Type, nil
}
