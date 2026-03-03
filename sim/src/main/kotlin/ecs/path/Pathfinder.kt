package starkraft.sim.ecs.path

import kotlin.math.abs
import kotlin.math.min
import starkraft.sim.ecs.MapGrid
import starkraft.sim.ecs.OccupancyGrid

class Pathfinder(
    private val map: MapGrid,
    private val occ: OccupancyGrid,
    private val allowCornerCut: Boolean = false
) {
    private val w = map.width
    private val h = map.height
    private val size = w * h

    private val visitId = IntArray(size)
    private val state = ByteArray(size)
    private val STATE_OPEN: Byte = 1
    private val STATE_CLOSED: Byte = 2
    private val gScore = FloatArray(size)
    private val fScore = FloatArray(size)
    private val parent = IntArray(size)
    private val heap = IntArray(size)
    private val heapPos = IntArray(size)
    private val scratchPath = IntArray(size)
    private var searchId = 1

    var lastNodesUsed: Int = 0
        private set
    var lastBudgetExhausted: Boolean = false
        private set

    private val dirX = intArrayOf(1, 0, -1, 0, 1, 1, -1, -1)
    private val dirY = intArrayOf(0, 1, 0, -1, 1, -1, 1, -1)

    fun findPath(sx: Int, sy: Int, gx: Int, gy: Int, maxNodes: Int, out: IntArray, allowOccupiedGoal: Boolean = false): Int {
        lastNodesUsed = 0
        lastBudgetExhausted = false
        if (!map.inBounds(sx, sy) || !map.inBounds(gx, gy)) return 0
        if (!map.isPassable(gx, gy)) return 0
        if (occ.isBlocked(gx, gy) && !(sx == gx && sy == gy) && !allowOccupiedGoal) return 0
        if (sx == gx && sy == gy) {
            out[0] = pack(sx, sy)
            return 1
        }

        searchId++
        if (searchId == Int.MAX_VALUE) {
            java.util.Arrays.fill(visitId, 0)
            searchId = 1
        }

        var heapSize = 0
        val start = pack(sx, sy)
        val goal = pack(gx, gy)
        open(start, g = 0f, f = heuristic(sx, sy, gx, gy))
        heap[heapSize] = start
        heapPos[start] = heapSize
        heapSize++

        while (heapSize > 0) {
            if (lastNodesUsed >= maxNodes) {
                lastBudgetExhausted = true
                return 0
            }
            val current = heapPop(heap, heapPos, fScore, heapSize).also { heapSize-- }
            state[current] = STATE_CLOSED
            lastNodesUsed++
            if (current == goal) {
                return buildPath(start, goal, out)
            }
            val cx = current % w
            val cy = current / w
            val gCur = gScore[current]

            for (i in 0 until 8) {
                val nx = cx + dirX[i]
                val ny = cy + dirY[i]
                if (!map.inBounds(nx, ny)) continue
                if (!map.isPassable(nx, ny)) continue
                if (occ.isBlocked(nx, ny) && !(allowOccupiedGoal && nx == gx && ny == gy)) continue
                if (i >= 4 && !allowCornerCut) {
                    val ox = cx + dirX[i]
                    val oy = cy
                    val px = cx
                    val py = cy + dirY[i]
                    if (!map.isPassable(ox, oy) || occ.isBlocked(ox, oy)) continue
                    if (!map.isPassable(px, py) || occ.isBlocked(px, py)) continue
                }

                val n = pack(nx, ny)
                val step = if (i < 4) 1f else 1.41421356f
                val ng = gCur + step * map.cost(nx, ny)
                val visited = visitId[n] == searchId
                if (!visited || (state[n] != STATE_CLOSED && ng < gScore[n])) {
                    parent[n] = current
                    val f = ng + heuristic(nx, ny, gx, gy)
                    if (!visited || state[n] == 0.toByte()) {
                        open(n, ng, f)
                        heap[heapSize] = n
                        heapPos[n] = heapSize
                        heapSize++
                        heapUp(heap, heapPos, fScore, heapSize - 1)
                    } else if (state[n] == STATE_OPEN && ng < gScore[n]) {
                        gScore[n] = ng
                        fScore[n] = f
                        heapUp(heap, heapPos, fScore, heapPos[n])
                    }
                }
            }
        }
        return 0
    }

    private fun open(n: Int, g: Float, f: Float) {
        visitId[n] = searchId
        state[n] = STATE_OPEN
        gScore[n] = g
        fScore[n] = f
        parent[n] = -1
    }

    private fun buildPath(start: Int, goal: Int, out: IntArray): Int {
        var len = 0
        var cur = goal
        while (cur != -1 && cur != start) {
            scratchPath[len++] = cur
            cur = parent[cur]
        }
        scratchPath[len++] = start

        // reverse in-place
        var i = 0
        var j = len - 1
        while (i < j) {
            val tmp = scratchPath[i]
            scratchPath[i] = scratchPath[j]
            scratchPath[j] = tmp
            i++; j--
        }

        // compress collinear segments
        if (len == 0) return 0
        var outLen = 0
        var prev = scratchPath[0]
        var prevDx = 0
        var prevDy = 0
        out[outLen++] = prev
        for (k in 1 until len) {
            val curNode = scratchPath[k]
            val dx = (curNode % w) - (prev % w)
            val dy = (curNode / w) - (prev / w)
            if (k == 1) {
                prevDx = dx
                prevDy = dy
            } else if (dx != prevDx || dy != prevDy) {
                out[outLen++] = prev
                prevDx = dx
                prevDy = dy
            }
            prev = curNode
        }
        out[outLen++] = prev
        return outLen
    }

    private fun heuristic(x: Int, y: Int, gx: Int, gy: Int): Float {
        val dx = abs(gx - x)
        val dy = abs(gy - y)
        val minD = min(dx, dy).toFloat()
        val maxD = (dx + dy).toFloat()
        return maxD + (1.41421356f - 2f) * minD
    }

    private fun pack(x: Int, y: Int): Int = y * w + x
}

private fun heapPop(heap: IntArray, heapPos: IntArray, fScore: FloatArray, size: Int): Int {
    val min = heap[0]
    val last = heap[size - 1]
    heap[0] = last
    heapPos[last] = 0
    heapPos[min] = -1
    heapDown(heap, heapPos, fScore, 0, size - 1)
    return min
}

private fun heapUp(heap: IntArray, heapPos: IntArray, fScore: FloatArray, idxStart: Int) {
    var idx = idxStart
    while (idx > 0) {
        val p = (idx - 1) / 2
        if (fScore[heap[idx]] < fScore[heap[p]]) {
            val tmp = heap[idx]
            heap[idx] = heap[p]
            heap[p] = tmp
            heapPos[heap[idx]] = idx
            heapPos[heap[p]] = p
            idx = p
        } else break
    }
}

private fun heapDown(heap: IntArray, heapPos: IntArray, fScore: FloatArray, idxStart: Int, size: Int) {
    var idx = idxStart
    while (true) {
        val l = idx * 2 + 1
        val r = l + 1
        if (l >= size) break
        val smallest = if (r < size && fScore[heap[r]] < fScore[heap[l]]) r else l
        if (fScore[heap[smallest]] < fScore[heap[idx]]) {
            val tmp = heap[idx]
            heap[idx] = heap[smallest]
            heap[smallest] = tmp
            heapPos[heap[idx]] = idx
            heapPos[heap[smallest]] = smallest
            idx = smallest
        } else break
    }
}
