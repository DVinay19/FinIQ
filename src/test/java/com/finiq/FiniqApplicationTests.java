
package com.finiq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FiniqApplicationTests {

    @Test
    void applicationClassUsesExpectedPackage() {
        assertEquals(
            "com.finiq",
            FiniqApplication.class.getPackageName()
        );
    }
}
