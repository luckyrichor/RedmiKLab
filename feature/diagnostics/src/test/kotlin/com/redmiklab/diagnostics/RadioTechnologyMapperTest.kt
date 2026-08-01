package com.redmiklab.diagnostics

import com.redmiklab.model.RadioTechnology
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioTechnologyMapperTest {
    @Test
    fun maps_nr_and_lte_to_5g_and_4g() {
        assertEquals(RadioTechnology.FiveG, RadioTechnologyMapper.fromNetworkType(20))
        assertEquals(RadioTechnology.FourG, RadioTechnologyMapper.fromNetworkType(13))
    }
}
