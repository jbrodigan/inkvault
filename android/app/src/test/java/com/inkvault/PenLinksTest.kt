package com.inkvault

import com.google.common.truth.Truth.assertThat
import com.inkvault.pen.PenLinks
import org.junit.Test

class PenLinksTest {
    @Test fun lamy_pointsToLamy() {
        assertThat(PenLinks.officialUrl("LAMY_safari")).isEqualTo(PenLinks.LAMY)
        assertThat(PenLinks.officialUrl("NWP-F80")).isEqualTo(PenLinks.LAMY)
    }

    @Test fun neo_pointsToNeolab() {
        assertThat(PenLinks.officialUrl("Neosmartpen_M1+")).isEqualTo(PenLinks.NEOLAB)
        assertThat(PenLinks.officialUrl("NWP-F55")).isEqualTo(PenLinks.NEOLAB)
    }

    @Test fun unknownOrBlank_fallsBackToSdk() {
        assertThat(PenLinks.officialUrl(null)).isEqualTo(PenLinks.SDK)
        assertThat(PenLinks.officialUrl("")).isEqualTo(PenLinks.SDK)
        assertThat(PenLinks.officialUrl("Mystery")).isEqualTo(PenLinks.SDK)
    }
}
