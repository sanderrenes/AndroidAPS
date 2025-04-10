package app.aaps.implementation.iob

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.math.abs

/**
 * Utility class for calculating glucose deltas using linearly interpolated glucose values
 */
object DeltaCalculator {

    /**
     * Calculate the 5-minute delta using weighted averages of measurements around the target time 
     * 
     * @param currentGlucose The current glucose value
     * @param currentTime The timestamp of the current glucose value
     * @param glucoseReadings List of glucose readings sorted by time (newest first)
     * @param logger Logger for debugging
     * @return The calculated 5-minute delta in mg/dL/5min
     */
    fun calculate5MinDelta(
        currentGlucose: Double,
        currentTime: Long,
        glucoseReadings: List<GV>,
        logger: AAPSLogger
    ): Double {
        
        // Target time calculation, set to 5 minutes for innitial development
        var deltaLength = 5
        
        val targetTime = currentTime - deltaLength * 1000L * 60
        
        // For now I have stuck with the 2.5 minute intervals used in the current implementations (17.5-15  and 42.5-40 etc)
        // Given the current issues (2025-04-10) with the FSL2 1,2, or 6 minute readings, 
        // it makes sense to increase these to 3.5 minute to ensure we capture at least one of the 6 minute readings in each bucket
        // Define the interval to look for measurements (2.5 minutes on either side)

        val intervalSize = 2.5
        val intervalStart = targetTime - intervalSize * 60 * 1000L
        val intervalEnd = targetTime + intervalSize * 60 * 1000L
        
        // Find measurements within the interval
        val measurementsInInterval = glucoseReadings.filter { 
            it.timestamp in intervalStart..intervalEnd && 
            it.value > 39
        }
        
        logger.debug(LTag.GLUCOSE, "Found ${measurementsInInterval.size} measurements in interval for 5-min ${deltaLength} delta")
        
        // If no measurements found in the interval, return 0.0
        if (measurementsInInterval.isEmpty()) {
            logger.debug(LTag.GLUCOSE, "No measurements found in interval 5-min around ${deltaLength} delta")
            return 0.0
        }
        
        // Split measurements into those before and after the target time
        val measurementsBefore = measurementsInInterval.filter { it.timestamp <= targetTime }
        val measurementsAfter = measurementsInInterval.filter { it.timestamp > targetTime }
        
        // If we have measurements on both sides, use the closest one from each side
        if (measurementsBefore.isNotEmpty() && measurementsAfter.isNotEmpty()) {
            // Find the closest measurement before the target time
            val closestBefore = measurementsBefore.maxByOrNull { it.timestamp }!!
            
            // Find the closest measurement after the target time
            val closestAfter = measurementsAfter.minByOrNull { it.timestamp }!!
            
            // Calculate time differences in milliseconds
            val timeDiffBefore = abs(closestBefore.timestamp - targetTime)
            val timeDiffAfter = abs(closestAfter.timestamp - targetTime)
            val totalTimeDiff = abs(closestAfter.timestamp - closestBefore.timestamp)
            
            // Calculate weights
            val weightBefore = 1.0 - (timeDiffBefore.toDouble() / totalTimeDiff.toDouble())
            val weightAfter = 1.0 - (timeDiffAfter.toDouble() / totalTimeDiff.toDouble())
            
            // Normalize weights to sum to 1.0 
            // this should not be necessary, as the weights automatically sum to 1, but rounding errors can get in the way
            // if the weights do not add up to 1, this biases the glucose calculation, so an extra normalization is used
            val totalWeight = weightBefore + weightAfter
            val normalizedWeightBefore = weightBefore / totalWeight
            val normalizedWeightAfter = weightAfter / totalWeight
            
            // Calculate weighted average of the two measurements
            val expectedGlucose = (closestBefore.recalculated * normalizedWeightBefore) + 
                                 (closestAfter.recalculated * normalizedWeightAfter)
            
            // Calculate the delta (scaled to 5 minutes)
            val delta = 5.0 * (currentGlucose - expectedGlucose) /  deltaLength
            
            logger.debug(LTag.GLUCOSE, "5-min delta calculation for ${deltaLength} delta: current=$currentGlucose, expected=$expectedGlucose, delta=$delta")
            logger.debug(LTag.GLUCOSE, "Weights: before=${normalizedWeightBefore}, after=${normalizedWeightAfter}")
            
            return delta
        }
        
        // If we only have measurements on one side, use the closest one, note that by selection earlier on we only ever pick measurements
        // that are at most ${intervalSize} minutes away from the target time.

        val measurementsToUse = if (measurementsBefore.isNotEmpty()) measurementsBefore else measurementsAfter
        val closestMeasurement = measurementsToUse.minByOrNull { abs(it.timestamp - targetTime) }!!
        
        // Calculate time difference in 5 minute units
        val timeDiffMs = abs(currentTime  - closestMeasurement.timestamp)/(1000*60*5)
        
        // Calculate the delta (scaled to 5 minutes)
        val delta = (currentGlucose - closestMeasurement.recalculated) / timeDiffMs
        
        logger.debug(LTag.GLUCOSE, "Single-sided measurement found at ${timeDiffMinutes} minutes from target, delta: $delta")
        return delta
    }
} 