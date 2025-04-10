package app.aaps.implementation.iob

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations


//created by SR 09-04-2025, not yet checked
// AI went into this

class DeltaCalculatorTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var dateUtil: DateUtil

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Mockito.`when`(aapsLogger.debug(Mockito.any(), Mockito.any())).thenReturn(Unit)
    }

    @Test
    fun `test exact 5-minute interval with two measurements`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements at exactly 5 minutes and 10 minutes ago
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 80.0,
                timestamp = T.mins(0).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected delta: (120 - 100) / 5 * 5 = 20 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(20.0)
    }

    @Test
    fun `test non-exact 5-minute interval with two measurements`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements at 4.5 minutes and 9.5 minutes ago
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).plus(T.secs(30)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 80.0,
                timestamp = T.secs(30).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected delta: (120 - 100) / 4.5 * 5 = 22.22 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(22.22)
    }

    @Test
    fun `test measurements on both sides of 5-minute mark`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements at 4 minutes and 6 minutes ago
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 105.0,
                timestamp = T.mins(4).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 95.0,
                timestamp = T.mins(6).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: weighted average of 105 and 95 based on distance from 5-minute mark
        // Weight for 105: 1 - (1/2) = 0.5
        // Weight for 95: 1 - (1/2) = 0.5
        // Expected glucose at 5 minutes: 105 * 0.5 + 95 * 0.5 = 100
        // Delta: (120 - 100) / 5 * 5 = 20 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(20.0)
    }

    @Test
    fun `test measurements with different distances from 5-minute mark`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements at 3 minutes and 7 minutes ago
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 110.0,
                timestamp = T.mins(3).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(7).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: weighted average of 110 and 90 based on distance from 5-minute mark
        // Weight for 110: 1 - (2/4) = 0.5
        // Weight for 90: 1 - (2/4) = 0.5
        // Expected glucose at 5 minutes: 110 * 0.5 + 90 * 0.5 = 100
        // Delta: (120 - 100) / 5 * 5 = 20 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(20.0)
    }

    @Test
    fun `test measurements with very small time differences`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements at 4.9 minutes and 5.1 minutes ago
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 101.0,
                timestamp = T.mins(4).plus(T.secs(54)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 99.0,
                timestamp = T.mins(5).plus(T.secs(6)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: weighted average of 101 and 99 based on distance from 5-minute mark
        // Weight for 101: 1 - (0.1/0.2) = 0.5
        // Weight for 99: 1 - (0.1/0.2) = 0.5
        // Expected glucose at 5 minutes: 101 * 0.5 + 99 * 0.5 = 100
        // Delta: (120 - 100) / 5 * 5 = 20 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(20.0)
    }

    @Test
    fun `test measurements with missing data`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements at 3 minutes and 8 minutes ago (gap around 5-minute mark)
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 110.0,
                timestamp = T.mins(3).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 85.0,
                timestamp = T.mins(8).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: weighted average of 110 and 85 based on distance from 5-minute mark
        // Weight for 110: 1 - (2/5) = 0.6
        // Weight for 85: 1 - (3/5) = 0.4
        // Expected glucose at 5 minutes: 110 * 0.6 + 85 * 0.4 = 100
        // Delta: (120 - 100) / 5 * 5 = 20 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(20.0)
    }

    @Test
    fun `test with single measurement within 5-minute mark`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create a single measurement at 4 minutes ago
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(6).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: (120 - 100) / (10-6) * 5 = 25 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(25.0)
    }

    @Test
    fun `test with single measurement outside 5-minute mark`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 130.0

        // Create a single measurement at 6 minutes ago
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(4).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: (130 - 100) / (10 - 6) * 5 = 25 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(25.0)
    }

    @Test
    fun `test with measurements at equal distance from 5-minute mark`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements at 3 minutes and 7 minutes ago (equal distance from 5-minute mark)
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 110.0,
                timestamp = T.mins(3).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(7).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: weighted average of 110 and 90 based on equal distance from 5-minute mark
        // Weight for 110: 1 - (2/4) = 0.5
        // Weight for 90: 1 - (2/4) = 0.5
        // Expected glucose at 5 minutes: 110 * 0.5 + 90 * 0.5 = 100
        // Delta: (120 - 100) / 5 * 5 = 20 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(20.0)
    }


    @Test
    fun `test with measurements at unequal distance from 5-minute mark, neg delta `() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 81.0

        // Create measurements at 4 minutes and 7 minutes ago 
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 96,0,
                timestamp = T.mins(4).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 81.0,
                timestamp = T.mins(7).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // Expected: weighted average of 80 and 95 based on unequal distance from 5-minute mark
        // Weight for 96: 1 - (1/3) = 2/3
        // Weight for 81: 1 - (2/3) = 1/3
        // Expected glucose at 5 minutes: 96 * 2/3 + 81 * 1/3 = 91
        // Delta: (81 - 91) / 5 * 5 = -10 mg/dL/5min
        assertThat(delta).isWithin(0.01).of(-10.0)
    }




    @Test
    fun `test with measurements outside the interval`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements outside the 2.5-minute interval around the 5-minute mark
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 110.0,
                timestamp = T.mins(2).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(8).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // No measurements in the interval, should return 0.0
        assertThat(delta).isEqualTo(0.0)
    }

    @Test
    fun `test with measurements with low glucose values`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Create measurements with values <= 39 (should be filtered out)
        val measurements = listOf(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 39.0,
                timestamp = T.mins(4).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            ),
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 38.0,
                timestamp = T.mins(6).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // No valid measurements (all <= 39), should return 0.0
        assertThat(delta).isEqualTo(0.0)
    }

    @Test
    fun `test with empty measurements list`() {
        // Current time
        val currentTime = T.mins(10).msecs()
        val currentGlucose = 120.0

        // Empty measurements list
        val measurements = emptyList<GV>()

        val delta = DeltaCalculator.calculate5MinDelta(currentGlucose, currentTime, measurements, aapsLogger)
        
        // No measurements, should return 0.0
        assertThat(delta).isEqualTo(0.0)
    }
} 