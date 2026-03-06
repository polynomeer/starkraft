package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/starkraft/client/pkg/headless"
	"github.com/starkraft/client/pkg/protocol"
)

func main() {
	url := flag.String("url", "ws://127.0.0.1:8080/ws", "server websocket URL")
	name := flag.String("name", "cli", "client name")
	room := flag.String("room", "default", "room id")
	scriptPath := flag.String("script", "", "optional JSON script path for auto command batches")
	flag.Parse()

	c, err := headless.Dial(*url, "dev", *name, room)
	if err != nil {
		panic(err)
	}
	defer c.Close()

	fmt.Printf("connected as %s\n", c.ClientID())
	var currentTick atomic.Int64
	var requestSeq atomic.Int64
	buffer := &headless.SnapshotBuffer{}
	selected := make([]int, 0, 8)
	groups := newControlGroups()
	statuses := newRequestStatusBook()
	hud := newHudState(c.ClientID())
	scriptBatches := loadScriptBatches(*scriptPath)
	nextScriptIndex := 0

	go func() {
		for s := range c.SnapshotCh {
			currentTick.Store(int64(s.Tick))
			buffer.Push(s)
			hud.update(s)
			units := buffer.Interpolate(0.5)
			fmt.Printf("tick=%d worldHash=%d units=%d matchEnded=%v\n", s.Tick, s.WorldHash, len(s.Units), s.MatchEnded)
			if len(units) > 0 {
				u := units[0]
				fmt.Printf("  interp unit id=%d owner=%s pos=(%.2f,%.2f) hp=%d\n", u.ID, u.OwnerID, u.X, u.Y, u.HP)
			}
			if s.MatchEnded {
				fmt.Printf("match ended winner=%v\n", s.WinnerID)
			}
			for nextScriptIndex < len(scriptBatches) && scriptBatches[nextScriptIndex].Tick <= s.Tick+1 {
				batch := scriptBatches[nextScriptIndex]
				for i := range batch.Commands {
					if batch.Commands[i].RequestID == nil {
						req := fmt.Sprintf("script-%d-%d", batch.Tick, i+1)
						batch.Commands[i].RequestID = &req
					}
					statuses.onSubmit(*batch.Commands[i].RequestID, batch.Commands[i].CommandType, batch.Tick)
				}
				if err := c.SendBatch(batch.Tick, batch.Commands); err != nil {
					fmt.Printf("script send failed tick=%d: %v\n", batch.Tick, err)
					for i := range batch.Commands {
						if batch.Commands[i].RequestID != nil {
							statuses.onSendError(*batch.Commands[i].RequestID, err.Error())
						}
					}
				} else {
					fmt.Printf("script batch sent tick=%d commands=%d\n", batch.Tick, len(batch.Commands))
				}
				nextScriptIndex++
			}
		}
	}()
	go func() {
		for ack := range c.AckCh {
			fmt.Printf("ack tick=%d cmd=%s accepted=%v reason=%s\n", ack.Tick, ack.CommandType, ack.Accepted, ack.Reason)
			statuses.onAck(ack)
		}
	}()
	go func() {
		for end := range c.MatchEndCh {
			fmt.Printf("matchEnd tick=%d winner=%v\n", end.Tick, end.WinnerID)
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
			selected = sanitizeSelection(selected)
			fmt.Printf("selected=%v\n", selected)
		case "groupSave":
			slot, ok := parseGroupSlot(parts)
			if !ok {
				fmt.Println("usage: groupSave <0-9>")
				continue
			}
			groups.save(slot, selected)
			fmt.Printf("group %d saved=%v\n", slot, groups.recall(slot))
		case "groupRecall":
			slot, ok := parseGroupSlot(parts)
			if !ok {
				fmt.Println("usage: groupRecall <0-9>")
				continue
			}
			restored := groups.recall(slot)
			if restored == nil {
				fmt.Printf("group %d is empty\n", slot)
				continue
			}
			selected = restored
			fmt.Printf("selected=%v\n", selected)
		case "groupAdd":
			slot, ok := parseGroupSlot(parts)
			if !ok {
				fmt.Println("usage: groupAdd <0-9>")
				continue
			}
			restored := groups.recall(slot)
			if restored == nil {
				fmt.Printf("group %d is empty\n", slot)
				continue
			}
			selected = mergeUniqueIDs(selected, restored)
			fmt.Printf("selected=%v\n", selected)
		case "groups":
			groups.print()
		case "move":
			if len(parts) < 3 {
				fmt.Println("usage: move <x> <y>")
				continue
			}
			x, _ := strconv.ParseFloat(parts[1], 64)
			y, _ := strconv.ParseFloat(parts[2], 64)
			issue(c, int(currentTick.Load())+1, &requestSeq, statuses, protocol.WireCommand{CommandType: "move", UnitIDs: cloneInts(selected), X: &x, Y: &y})
		case "attack":
			if len(parts) < 2 {
				fmt.Println("usage: attack <targetUnitId>")
				continue
			}
			t, _ := strconv.Atoi(parts[1])
			issue(c, int(currentTick.Load())+1, &requestSeq, statuses, protocol.WireCommand{CommandType: "attack", UnitIDs: cloneInts(selected), TargetUnitID: &t})
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
			issue(c, int(currentTick.Load())+1, &requestSeq, statuses, protocol.WireCommand{CommandType: "build", X: &x, Y: &y, UnitType: ut})
		case "queue":
			var ut *string
			if len(parts) >= 2 {
				t := parts[1]
				ut = &t
			}
			issue(c, int(currentTick.Load())+1, &requestSeq, statuses, protocol.WireCommand{CommandType: "queue", UnitType: ut})
		case "surrender":
			issue(c, int(currentTick.Load())+1, &requestSeq, statuses, protocol.WireCommand{CommandType: "surrender"})
		case "snapshot":
			for _, u := range buffer.Interpolate(0.5) {
				fmt.Printf("unit id=%d owner=%s type=%s pos=(%.2f,%.2f) hp=%d\n", u.ID, u.OwnerID, u.TypeID, u.X, u.Y, u.HP)
			}
		case "status":
			statuses.print()
		case "hud":
			hud.print(statuses)
		default:
			fmt.Println("commands: select/groupSave/groupRecall/groupAdd/groups/move/attack/build/queue/surrender/snapshot/status/hud/quit")
		}
	}
}

func issue(
	c *headless.Client,
	tick int,
	seq *atomic.Int64,
	statuses *requestStatusBook,
	cmd protocol.WireCommand,
) {
	req := fmt.Sprintf("cli-%d-%d", tick, seq.Add(1))
	cmd.RequestID = &req
	statuses.onSubmit(req, cmd.CommandType, tick)
	if err := c.SendBatch(tick, []protocol.WireCommand{cmd}); err != nil {
		fmt.Printf("send failed: %v\n", err)
		statuses.onSendError(req, err.Error())
	}
}

func cloneInts(src []int) []int {
	out := make([]int, len(src))
	copy(out, src)
	return out
}

func mergeUniqueIDs(base []int, extra []int) []int {
	if len(extra) == 0 {
		return sanitizeSelection(base)
	}
	seen := make(map[int]struct{}, len(base)+len(extra))
	out := make([]int, 0, len(base)+len(extra))
	for _, id := range base {
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	for _, id := range extra {
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	return out
}

func sanitizeSelection(ids []int) []int {
	if len(ids) == 0 {
		return ids
	}
	return mergeUniqueIDs(nil, ids)
}

func parseGroupSlot(parts []string) (int, bool) {
	if len(parts) < 2 {
		return 0, false
	}
	slot, err := strconv.Atoi(parts[1])
	if err != nil || slot < 0 || slot > 9 {
		return 0, false
	}
	return slot, true
}

type controlGroups struct {
	slots [10][]int
}

func newControlGroups() *controlGroups {
	return &controlGroups{}
}

func (g *controlGroups) save(slot int, selected []int) {
	if slot < 0 || slot >= len(g.slots) {
		return
	}
	g.slots[slot] = cloneInts(sanitizeSelection(selected))
}

func (g *controlGroups) recall(slot int) []int {
	if slot < 0 || slot >= len(g.slots) {
		return nil
	}
	if len(g.slots[slot]) == 0 {
		return nil
	}
	return cloneInts(g.slots[slot])
}

func (g *controlGroups) print() {
	fmt.Println("control groups:")
	for slot := range g.slots {
		if len(g.slots[slot]) == 0 {
			continue
		}
		fmt.Printf("  %d: %v\n", slot, g.slots[slot])
	}
}

type requestStatus struct {
	RequestID string
	Command   string
	Tick      int
	State     string
	Reason    string
	UpdatedAt time.Time
}

type requestStatusBook struct {
	mu      sync.Mutex
	entries map[string]requestStatus
	order   []string
}

type hudState struct {
	ownerID      string
	tick         int
	worldHash    int64
	totalUnits   int
	myUnits      int
	enemyUnits   int
	matchEnded   bool
	winnerID     string
	myTypeCounts map[string]int
	enemyByOwner map[string]int
}

type scriptBatch struct {
	Tick     int                    `json:"tick"`
	Commands []protocol.WireCommand `json:"commands"`
}

func newRequestStatusBook() *requestStatusBook {
	return &requestStatusBook{
		entries: make(map[string]requestStatus, 128),
		order:   make([]string, 0, 128),
	}
}

func newHudState(ownerID string) *hudState {
	return &hudState{
		ownerID:      ownerID,
		myTypeCounts: make(map[string]int, 8),
		enemyByOwner: make(map[string]int, 8),
	}
}

func (h *hudState) update(snapshot protocol.SnapshotMessage) {
	h.tick = snapshot.Tick
	h.worldHash = snapshot.WorldHash
	h.totalUnits = len(snapshot.Units)
	h.myUnits = 0
	h.enemyUnits = 0
	h.matchEnded = snapshot.MatchEnded
	if snapshot.WinnerID != nil {
		h.winnerID = *snapshot.WinnerID
	} else {
		h.winnerID = ""
	}
	for k := range h.myTypeCounts {
		delete(h.myTypeCounts, k)
	}
	for k := range h.enemyByOwner {
		delete(h.enemyByOwner, k)
	}
	for _, unit := range snapshot.Units {
		if unit.OwnerID == h.ownerID {
			h.myUnits++
			h.myTypeCounts[unit.TypeID]++
		} else {
			h.enemyUnits++
			h.enemyByOwner[unit.OwnerID]++
		}
	}
}

func (h *hudState) print(statuses *requestStatusBook) {
	fmt.Printf(
		"hud tick=%d worldHash=%d totalUnits=%d myUnits=%d enemyUnits=%d matchEnded=%v winner=%s\n",
		h.tick,
		h.worldHash,
		h.totalUnits,
		h.myUnits,
		h.enemyUnits,
		h.matchEnded,
		blankIfEmpty(h.winnerID),
	)
	if len(h.myTypeCounts) > 0 {
		fmt.Printf("  my types: %s\n", formatCountMap(h.myTypeCounts))
	}
	if len(h.enemyByOwner) > 0 {
		fmt.Printf("  enemy owners: %s\n", formatCountMap(h.enemyByOwner))
	}
	pending, accepted, rejected, sendFailed := statuses.countByState()
	fmt.Printf(
		"  requests pending=%d accepted=%d rejected=%d sendFailed=%d\n",
		pending,
		accepted,
		rejected,
		sendFailed,
	)
}

func (b *requestStatusBook) onSubmit(requestID, command string, tick int) {
	b.mu.Lock()
	defer b.mu.Unlock()
	if _, ok := b.entries[requestID]; !ok {
		b.order = append(b.order, requestID)
	}
	b.entries[requestID] = requestStatus{
		RequestID: requestID,
		Command:   command,
		Tick:      tick,
		State:     "pending",
		UpdatedAt: time.Now(),
	}
}

func (b *requestStatusBook) onSendError(requestID, reason string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	entry := b.entries[requestID]
	entry.State = "sendFailed"
	entry.Reason = reason
	entry.UpdatedAt = time.Now()
	b.entries[requestID] = entry
}

func (b *requestStatusBook) onAck(ack protocol.CommandAckMessage) {
	if ack.RequestID == nil {
		return
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	entry, ok := b.entries[*ack.RequestID]
	if !ok {
		entry = requestStatus{RequestID: *ack.RequestID, Command: ack.CommandType, Tick: ack.Tick}
		b.order = append(b.order, *ack.RequestID)
	}
	if ack.Accepted {
		entry.State = "accepted"
		entry.Reason = ""
	} else {
		entry.State = "rejected"
		entry.Reason = ack.Reason
	}
	entry.UpdatedAt = time.Now()
	b.entries[*ack.RequestID] = entry
}

func (b *requestStatusBook) print() {
	b.mu.Lock()
	defer b.mu.Unlock()
	if len(b.order) == 0 {
		fmt.Println("status: no requests")
		return
	}
	start := 0
	if len(b.order) > 15 {
		start = len(b.order) - 15
	}
	fmt.Println("status (latest):")
	for _, requestID := range b.order[start:] {
		entry := b.entries[requestID]
		fmt.Printf(
			"  id=%s cmd=%s tick=%d state=%s reason=%s\n",
			entry.RequestID,
			entry.Command,
			entry.Tick,
			entry.State,
			entry.Reason,
		)
	}
}

func (b *requestStatusBook) countByState() (int, int, int, int) {
	b.mu.Lock()
	defer b.mu.Unlock()
	pending := 0
	accepted := 0
	rejected := 0
	sendFailed := 0
	for _, entry := range b.entries {
		switch entry.State {
		case "pending":
			pending++
		case "accepted":
			accepted++
		case "rejected":
			rejected++
		case "sendFailed":
			sendFailed++
		}
	}
	return pending, accepted, rejected, sendFailed
}

func formatCountMap(values map[string]int) string {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, key := range keys {
		parts = append(parts, fmt.Sprintf("%s=%d", key, values[key]))
	}
	return strings.Join(parts, ", ")
}

func blankIfEmpty(value string) string {
	if value == "" {
		return "-"
	}
	return value
}

func loadScriptBatches(path string) []scriptBatch {
	if path == "" {
		return nil
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		panic(fmt.Sprintf("read script: %v", err))
	}
	var batches []scriptBatch
	if err := json.Unmarshal(raw, &batches); err != nil {
		panic(fmt.Sprintf("parse script: %v", err))
	}
	for i := range batches {
		if batches[i].Tick < 0 {
			panic(fmt.Sprintf("script batch %d has negative tick", i))
		}
	}
	fmt.Printf("loaded script batches=%d path=%s\n", len(batches), path)
	return batches
}
