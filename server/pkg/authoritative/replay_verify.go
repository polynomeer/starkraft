package authoritative

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
)

type ReplayVerifySummary struct {
	Records        int
	HeaderCount    int
	CommandCount   int
	KeyframeCount  int
	MatchEndCount  int
	LastTick       int
	LastWorldHash  int64
}

type replayRecordType struct {
	RecordType string `json:"recordType"`
}

type replayKeyframeView struct {
	RecordType string `json:"recordType"`
	Tick       int    `json:"tick"`
	WorldHash  int64  `json:"worldHash"`
}

type replayCommandView struct {
	RecordType string `json:"recordType"`
	Ack        struct {
		Tick int `json:"tick"`
	} `json:"ack"`
}

type replayMatchEndView struct {
	RecordType string `json:"recordType"`
	Tick       int    `json:"tick"`
}

func VerifyReplayFile(path string) (ReplayVerifySummary, error) {
	file, err := os.Open(path)
	if err != nil {
		return ReplayVerifySummary{}, err
	}
	defer file.Close()

	summary := ReplayVerifySummary{}
	scanner := bufio.NewScanner(file)
	scanner.Buffer(make([]byte, 1024), 1024*1024)
	lastKeyframeTick := -1
	lastCommandTick := -1
	lineNo := 0
	for scanner.Scan() {
		lineNo++
		line := scanner.Bytes()
		var kind replayRecordType
		if err := json.Unmarshal(line, &kind); err != nil {
			return summary, fmt.Errorf("line %d: invalid json: %w", lineNo, err)
		}
		summary.Records++
		switch kind.RecordType {
		case "header":
			summary.HeaderCount++
			if lineNo != 1 {
				return summary, fmt.Errorf("line %d: header must be first record", lineNo)
			}
		case "command":
			summary.CommandCount++
			var cmd replayCommandView
			if err := json.Unmarshal(line, &cmd); err != nil {
				return summary, fmt.Errorf("line %d: invalid command record: %w", lineNo, err)
			}
			if cmd.Ack.Tick < lastCommandTick {
				return summary, fmt.Errorf("line %d: command ack tick regressed (%d < %d)", lineNo, cmd.Ack.Tick, lastCommandTick)
			}
			lastCommandTick = cmd.Ack.Tick
		case "keyframe":
			summary.KeyframeCount++
			var kf replayKeyframeView
			if err := json.Unmarshal(line, &kf); err != nil {
				return summary, fmt.Errorf("line %d: invalid keyframe record: %w", lineNo, err)
			}
			if kf.Tick <= lastKeyframeTick {
				return summary, fmt.Errorf("line %d: keyframe tick not increasing (%d <= %d)", lineNo, kf.Tick, lastKeyframeTick)
			}
			lastKeyframeTick = kf.Tick
			summary.LastTick = kf.Tick
			summary.LastWorldHash = kf.WorldHash
		case "matchEnd":
			summary.MatchEndCount++
			var end replayMatchEndView
			if err := json.Unmarshal(line, &end); err != nil {
				return summary, fmt.Errorf("line %d: invalid matchEnd record: %w", lineNo, err)
			}
			if summary.MatchEndCount > 1 {
				return summary, fmt.Errorf("line %d: duplicate matchEnd record", lineNo)
			}
			if end.Tick < summary.LastTick {
				return summary, fmt.Errorf("line %d: matchEnd tick regressed (%d < %d)", lineNo, end.Tick, summary.LastTick)
			}
			summary.LastTick = end.Tick
		default:
			return summary, fmt.Errorf("line %d: unknown recordType '%s'", lineNo, kind.RecordType)
		}
	}
	if err := scanner.Err(); err != nil {
		return summary, err
	}
	if summary.HeaderCount != 1 {
		return summary, fmt.Errorf("expected exactly one header record, got %d", summary.HeaderCount)
	}
	if summary.Records == 0 {
		return summary, fmt.Errorf("replay file is empty")
	}
	return summary, nil
}
