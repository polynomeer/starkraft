package protocol

import "testing"

func TestCompatibilityMatrix(t *testing.T) {
	if Compatibility(1, 1) != Compatible {
		t.Fatal("expected compatible")
	}
	if Compatibility(1, 2) != UpgradeClient {
		t.Fatal("expected upgrade client")
	}
	if Compatibility(2, 1) != UpgradeServer {
		t.Fatal("expected upgrade server")
	}
}
