package authoritative

import "testing"

func TestPercentileNs(t *testing.T) {
	sorted := []int64{10, 20, 30, 40, 50}
	if got := percentileNs(sorted, 50); got != 30 {
		t.Fatalf("p50 want=30 got=%d", got)
	}
	if got := percentileNs(sorted, 95); got != 40 {
		t.Fatalf("p95 want=40 got=%d", got)
	}
	if got := percentileNs(sorted, 99); got != 40 {
		t.Fatalf("p99 want=40 got=%d", got)
	}
}
