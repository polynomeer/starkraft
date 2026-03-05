package main

import (
	"flag"
	"fmt"
	"math"
	"time"

	"github.com/starkraft/client/pkg/headless"
	"github.com/starkraft/client/pkg/protocol"
)

func main() {
	url := flag.String("url", "ws://127.0.0.1:8080/ws", "server websocket URL")
	name := flag.String("name", "bot", "bot name")
	room := flag.String("room", "default", "room id")
	flag.Parse()

	c, err := headless.Dial(*url, "dev", *name, room)
	if err != nil {
		panic(err)
	}
	defer c.Close()
	fmt.Printf("bot connected as %s\n", c.ClientID())

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
			panic(err)
		case <-time.After(15 * time.Second):
			panic(fmt.Sprintf("bot timeout at tick %d", lastTick))
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
