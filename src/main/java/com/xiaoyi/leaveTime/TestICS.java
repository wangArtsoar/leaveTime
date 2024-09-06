package com.xiaoyi.leaveTime;

import org.junit.Assert;
import org.junit.Test;

import static com.xiaoyi.leaveTime.ICSParser.workTime;

public class TestICS {

    @Test
    public void TestWorkTime() {
        Assert.assertEquals(8.0, workTime("2024-02-01 09:00:00", "2024-02-01 18:00:00"), 2);
        Assert.assertEquals(4.0, workTime("2024-02-01 09:00:00", "2024-02-01 12:00:00"), 2);
        Assert.assertEquals(4.0, workTime("2024-02-01 14:00:00", "2024-02-01 18:00:00"), 2);
        Assert.assertEquals(0.0, workTime("2024-02-12 09:00:00", "2024-02-14 18:00:00"), 2);
        Assert.assertEquals(12.0, workTime("2024-02-17 09:00:00", "2024-02-19 14:00:00"), 2);
        Assert.assertEquals(0.0, workTime("2024-02-01 20:00:00", "2024-02-01 22:00:00"), 2);
    }
}
