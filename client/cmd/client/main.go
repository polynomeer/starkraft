package main

import (
	"bufio"
	"flag"
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync/atomic"

	"github.com/starkraft/client/pkg/headless"
	"github.com/starkraft/client/pkg/protocol"
)

func main() {
	url := flag.String("url", "ws://127.0.0.1:8080/ws", "server websocket URL")
	name := flag.String("name", "cli", "client name")
	room := flag.String("room", "default", "room id")
	flag.Parse()

	c, err := headless.Dial(*url, "dev", *name, room)
	if err != nil {
		panic(err)
	}
	defer c.Close()

	fmt.Printf("connected as %s\n", c.ClientID())
	var currentTick atomic.Int64
	buffer := &headless.SnapshotBuffer{}
	selected := make([]int, 0, 8)

	go func() {
		for s := range c.SnapshotCh {
			currentTick.Store(int64(s.Tick))
			buffer.Push(s)
			units := buffer.Interpolate(0.5)
			fmt.Printf("tick=%d worldHash=%d units=%d matchEnded=%v\n", s.Tick, s.WorldHash, len(s.Units), s.MatchEnded)
			if len(units) > 0 {
				u := units[0]
				fmt.Printf("  interp unit id=%d owner=%s pos=(%.2f,%.2f) hp=%d\n", u.ID, u.OwnerID, u.X, u.Y, u.HP)
			}
			if s.MatchEnded {
				fmt.Printf("match ended winner=%v\n", s.WinnerID)
			}
		}
	}()
	go func() {
		for ack := range c.AckCh {
			fmt.Printf("ack tick=%d cmd=%s accepted=%v reason=%s\n", ack.Tick, ack.CommandType, ack.Accepted, ack.Reason)
		}
	}()

	s := bufio.NewScanner(os.Stdin)
	for {
		fmt.Print("client> ")
		if !s.Scan() {
			return
		}
		line := strings.TrimSpace(s.Text())
		if line == "" {
			continue
		}
		if line == "quit" || line == "exit" {
			return
		}
		parts := strings.Fields(line)
		cmd := parts[0]
		switch cmd {
		case "select":
			selected = selected[:0]
			for _, p := range parts[1:] {
				id, err := strconv.Atoi(p)
				if err == nil {
					selected = append(selected, id)
				}
			}
			fmt.Printf("selected=%v\n", selected)
		case "move":
			if len(parts) < 3 {
				fmt.Println("usage: move <x> <y>")
				continue
			}
			x, _ := strconv.ParseFloat(parts[1], 64)
			y, _ := strconv.ParseFloat(parts[2], 64)
			issue(c, int(currentTick.Load())+1, protocol.WireCommand{CommandType: "move", UnitIDs: cloneInts(selected), X: &x, Y: &y})
		case "attack":
			if len(parts) < 2 {
				fmt.Println("usage: attack <targetUnitId>")
				continue
			}
			t, _ := strconv.Atoi(parts[1])
			issue(c, int(currentTick.Load())+1, protocol.WireCommand{CommandType: "attack", UnitIDs: cloneInts(selected), TargetUnitID: &t})
		case "build":
			if len(parts) < 3 {
				fmt.Println("usage: build <x> <y> [unitType]")
				continue
			}
			x, _ := strconv.ParseFloat(parts[1], 64)
			y, _ := strconv.ParseFloat(parts[2], 64)
			var ut *string
			if len(parts) >= 4 {
				t := parts[3]
				ut = &t
			}
			issue(c, int(currentTick.Load())+1, protocol.WireCommand{CommandType: "build", X: &x, Y: &y, UnitType: ut})
		case "queue":
			var ut *string
			if len(parts) >= 2 {
				t := parts[1]
				ut = &t
			}
			issue(c, int(currentTick.Load())+1, protocol.WireCommand{CommandType: "queue", UnitType: ut})
		case "snapshot":
			for _, u := range buffer.Interpolate(0.5) {
				fmt.Printf("unit id=%d owner=%s type=%s pos=(%.2f,%.2f) hp=%d\n", u.ID, u.OwnerID, u.TypeID, u.X, u.Y, u.HP)
			}
		default:
			fmt.Println("commands: select/move/attack/build/queue/snapshot/quit")
		}
	}
}

func issue(c *headless.Client, tick int, cmd protocol.WireCommand) {
	req := fmt.Sprintf("cli-%d", tick)
	cmd.RequestID = &req
	if err := c.SendBatch(tick, []protocol.WireCommand{cmd}); err != nil {
		fmt.Printf("send failed: %v\n", err)
	}
}

func cloneInts(src []int) []int {
	out := make([]int, len(src))
	copy(out, src)
	return out
}
