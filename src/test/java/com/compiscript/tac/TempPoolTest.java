package com.compiscript.tac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempPoolTest {

    @Test
    void reusesTheLowestFreeTemporary() {
        TempPool pool = new TempPool();
        String first = pool.acquire();
        String second = pool.acquire();
        pool.release(first);
        String reused = pool.acquire();

        assertEquals("t0", first);
        assertEquals("t1", second);
        assertEquals("t0", reused);
        assertTrue(pool.maxLive() <= 2);
    }

    @Test
    void expressionPatternKeepsTwoLiveTemps() {
        TempPool pool = new TempPool();
        String left = pool.acquire();
        String right = pool.acquire();
        pool.release(left);
        pool.release(right);
        String product = pool.acquire();

        assertEquals("t0", product);
        assertEquals(2, pool.maxLive());
    }
}
