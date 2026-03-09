package main

import (
	"flag"
	"fmt"
	"math"
	"os"
	"strings"
	"time"

	"github.com/starkraft/client/pkg/headless"
	"github.com/starkraft/client/pkg/protocol"
)

func main() {
	url := flag.String("url", "ws://127.0.0.1:8080/ws", "server websocket URL")
	name := flag.String("name", "bot", "bot name")
	room := flag.String("room", "default", "room id")
	simVersion := flag.String("simVersion", "dev", "sim version tag for protocol envelope")
	resumeToken := flag.String("resumeToken", "", "optional resume token from prior handshake")
	flag.Parse()
	normalizedSimVersion := strings.TrimSpace(*simVersion)
	if normalizedSimVersion == "" {
		fmt.Fprintln(os.Stderr, "invalid --simVersion: value must be non-empty")
		os.Exit(1)
	}

	var resumePtr *string
	if strings.TrimSpace(*resumeToken) != "" {
		token := strings.TrimSpace(*resumeToken)
		resumePtr = &token
	}
	c, err := headless.DialWithResume(*url, normalizedSimVersion, *name, room, resumePtr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "connect failed: %v\n", err)
		os.Exit(1)
	}
	defer c.Close()
	fmt.Printf("bot connected as %s\n", c.ClientID())
	if token := c.ResumeToken(); token != nil && strings.TrimSpace(*token) != "" {
		fmt.Printf("bot resumeToken=%s\n", *token)
	}

	lastTick := 0
	for {
		select {
		case s := <-c.SnapshotCh:
			lastTick = s.Tick
			if s.MatchEnded {
				fmt.Printf("bot %s match ended winner=%v\n", c.ClientID(), s.WinnerID)
				return
			}
			myUnits := make([]protocol.SnapshotUnit, 0)
			enemies := make([]protocol.SnapshotUnit, 0)
			for _, u := range s.Units {
				if u.OwnerID == c.ClientID() {
					myUnits = append(myUnits, u)
				} else {
					enemies = append(enemies, u)
				}
			}
			if len(myUnits) == 0 {
				continue
			}

			if s.Tick%20 == 0 {
				ux := myUnits[0].X + 1
				uy := myUnits[0].Y + 1
				t := "Worker"
				req := fmt.Sprintf("bot-build-%d", s.Tick)
				_ = c.SendBatch(s.Tick+1, []protocol.WireCommand{{CommandType: "build", RequestID: &req, X: &ux, Y: &uy, UnitType: &t}})
			}
			if s.Tick%15 == 0 {
				t := "Soldier"
				req := fmt.Sprintf("bot-queue-%d", s.Tick)
				_ = c.SendBatch(s.Tick+1, []protocol.WireCommand{{CommandType: "queue", RequestID: &req, UnitType: &t}})
			}

			if len(enemies) == 0 {
				continue
			}
			target := nearestEnemy(myUnits[0], enemies)
			ids := make([]int, 0, len(myUnits))
			for _, u := range myUnits {
				ids = append(ids, u.ID)
			}
			dx := myUnits[0].X - target.X
			dy := myUnits[0].Y - target.Y
			d2 := dx*dx + dy*dy
			req := fmt.Sprintf("bot-%d", s.Tick)
			if d2 <= 36 {
				tid := target.ID
				_ = c.SendBatch(s.Tick+1, []protocol.WireCommand{{CommandType: "attack", RequestID: &req, UnitIDs: ids, TargetUnitID: &tid}})
			} else {
				x, y := target.X, target.Y
				_ = c.SendBatch(s.Tick+1, []protocol.WireCommand{{CommandType: "move", RequestID: &req, UnitIDs: ids, X: &x, Y: &y}})
			}
		case ack := <-c.AckCh:
			if !ack.Accepted {
				fmt.Printf("bot ack rejected cmd=%s reason=%s\n", ack.CommandType, ack.Reason)
			}
		case err := <-c.ErrCh:
			fmt.Fprintf(os.Stderr, "stream error: %v\n", err)
			os.Exit(1)
		case <-time.After(15 * time.Second):
			fmt.Fprintf(os.Stderr, "bot timeout at tick %d\n", lastTick)
			os.Exit(1)
		}
	}
}

func nearestEnemy(from protocol.SnapshotUnit, enemies []protocol.SnapshotUnit) protocol.SnapshotUnit {
	best := enemies[0]
	bestD := math.MaxFloat64
	for _, e := range enemies {
		dx := from.X - e.X
		dy := from.Y - e.Y
		d := dx*dx + dy*dy
		if d < bestD {
			best = e
			bestD = d
		}
	}
	return best
}
