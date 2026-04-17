package com.brighterly.testlinks

import com.brighterly.testlinks.scaffold.TestFileScaffolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestFileScaffolderPathTest {

    @Test
    fun `service nested path maps to tests Services mirror and picks SERVICE kind`() {
        val result = TestFileScaffolder.mapSourceToTestLocation(
            "app/Services/Customer/Activation/ActivationService.php",
        )
        assertEquals("tests/Services/Customer/Activation", result?.dir)
        assertEquals("Tests\\Services\\Customer\\Activation", result?.namespace)
        assertEquals(TestFileScaffolder.Kind.SERVICE, result?.kind)
    }

    @Test
    fun `controller path maps to tests Controllers mirror dropping Http and picks CONTROLLER kind`() {
        val result = TestFileScaffolder.mapSourceToTestLocation(
            "app/Http/Controllers/AdminApi/Agents/AgentsController.php",
        )
        assertEquals("tests/Controllers/AdminApi/Agents", result?.dir)
        assertEquals("Tests\\Controllers\\AdminApi\\Agents", result?.namespace)
        assertEquals(TestFileScaffolder.Kind.CONTROLLER, result?.kind)
    }

    @Test
    fun `non service or controller returns null`() {
        assertNull(TestFileScaffolder.mapSourceToTestLocation("app/Models/User.php"))
    }

    @Test
    fun `top level service with no subdir`() {
        val result = TestFileScaffolder.mapSourceToTestLocation("app/Services/FooService.php")
        assertEquals("tests/Services", result?.dir)
        assertEquals("Tests\\Services", result?.namespace)
        assertEquals(TestFileScaffolder.Kind.SERVICE, result?.kind)
    }
}
