package authoritative

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/starkraft/server/pkg/protocol"
)

func TestHealthEndpointReturnsServerStatus(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:   "test-sim",
		TickInterval: 20 * time.Millisecond,
	})
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	resp, err := http.Get(ts.URL + "/healthz")
	if err != nil {
		t.Fatalf("get healthz: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected status 200, got %d", resp.StatusCode)
	}

	var payload healthResponse
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("decode health payload: %v", err)
	}
	if payload.Status != "ok" {
		t.Fatalf("expected status ok, got %+v", payload)
	}
	if payload.ProtocolVersion != protocol.CurrentProtocolVersion {
		t.Fatalf("expected protocol version %d, got %d", protocol.CurrentProtocolVersion, payload.ProtocolVersion)
	}
	if payload.SimVersion != "test-sim" {
		t.Fatalf("expected sim version test-sim, got %s", payload.SimVersion)
	}
}

func TestAdminStatsShowsRoomAndResumeSession(t *testing.T) {
	srv := NewServer(Config{
		SimVersion:   "test-sim",
		TickInterval: 20 * time.Millisecond,
		ResumeWindow: 2 * time.Second,
	})
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	roomID := "ops-room"
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("dial websocket: %v", err)
	}
	hsRaw, _ := json.Marshal(protocol.HandshakeMessage{
		Type:          "handshake",
		ClientName:    "bot-a",
		RequestedRoom: &roomID,
	})
	if err := conn.WriteJSON(protocol.ProtocolEnvelope{
		ProtocolVersion: protocol.CurrentProtocolVersion,
		SimVersion:      "test",
		Message:         hsRaw,
	}); err != nil {
		t.Fatalf("write handshake: %v", err)
	}
	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	_ = readUntilMessageType(t, conn, "handshakeAck")
	_ = readUntilMessageType(t, conn, "snapshot")
	_ = conn.Close()

	waitForResumeSessions(t, srv, 1)

	resp, err := http.Get(ts.URL + "/admin/stats")
	if err != nil {
		t.Fatalf("get admin stats: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected status 200, got %d", resp.StatusCode)
	}

	var payload adminStatsResponse
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		t.Fatalf("decode admin stats payload: %v", err)
	}
	if len(payload.Rooms) != 1 || payload.Rooms[0].ID != roomID {
		t.Fatalf("expected one room %s, got %+v", roomID, payload.Rooms)
	}
	if len(payload.ResumeSessions) != 1 || payload.ResumeSessions[0].RoomID != roomID {
		t.Fatalf("expected one resume session in room %s, got %+v", roomID, payload.ResumeSessions)
	}
	if payload.ResumeSessions[0].ExpiresInMs <= 0 {
		t.Fatalf("expected positive resume expiry, got %+v", payload.ResumeSessions[0])
	}
}

func waitForResumeSessions(t *testing.T, srv *Server, want int) {
	t.Helper()
	deadline := time.Now().Add(500 * time.Millisecond)
	for {
		srv.mu.Lock()
		count := len(srv.resumeSessions)
		srv.mu.Unlock()
		if count >= want {
			return
		}
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for %d resume session(s), got %d", want, count)
		}
		time.Sleep(5 * time.Millisecond)
	}
}

func TestAdminStatsDoesNotExposeResumeTokens(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test-sim", TickInterval: 20 * time.Millisecond})
	srv.mu.Lock()
	token, err := srv.issueResumeTokenLocked("player-1", "bot-a", "ops-room")
	srv.mu.Unlock()
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/admin/stats", nil)
	req.RemoteAddr = "127.0.0.1:4242"
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}
	if strings.Contains(rec.Body.String(), token) {
		t.Fatalf("admin stats leaked resume token: %s", rec.Body.String())
	}
	var payload adminStatsResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &payload); err != nil {
		t.Fatalf("decode admin stats payload: %v", err)
	}
	if len(payload.ResumeSessions) != 1 || !payload.ResumeSessions[0].Connected {
		t.Fatalf("expected one connected resume session, got %+v", payload.ResumeSessions)
	}
}

func TestAdminStatsRejectsNonLoopbackWithoutToken(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test-sim", TickInterval: 20 * time.Millisecond})
	req := httptest.NewRequest(http.MethodGet, "/admin/stats", nil)
	req.RemoteAddr = "203.0.113.7:4242"
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)
	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected status 403, got %d", rec.Code)
	}
}

func TestAdminStatsRequiresBearerTokenWhenConfigured(t *testing.T) {
	srv := NewServer(Config{SimVersion: "test-sim", TickInterval: 20 * time.Millisecond, AdminToken: "s3cret"})
	cases := []struct {
		name   string
		header string
		want   int
	}{
		{name: "missing", header: "", want: http.StatusUnauthorized},
		{name: "wrong", header: "Bearer nope", want: http.StatusUnauthorized},
		{name: "no scheme", header: "s3cret", want: http.StatusUnauthorized},
		{name: "valid", header: "Bearer s3cret", want: http.StatusOK},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodGet, "/admin/stats", nil)
			// Token auth applies regardless of source address.
			req.RemoteAddr = "203.0.113.7:4242"
			if tc.header != "" {
				req.Header.Set("Authorization", tc.header)
			}
			rec := httptest.NewRecorder()
			srv.Handler().ServeHTTP(rec, req)
			if rec.Code != tc.want {
				t.Fatalf("expected status %d, got %d", tc.want, rec.Code)
			}
		})
	}
}
