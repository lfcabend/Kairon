package com.kairon.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProblemDetailTest {

    @Test
    fun parsesAPlainApiExceptionShapedBody() {
        val body = """
            {"type":"https://kairon.app/problems/not-found","title":"Not Found","status":404,"detail":"Task not found."}
        """.trimIndent()

        val problem = ProblemDetail.parse(body)

        assertEquals(404, problem?.status)
        assertEquals("Task not found.", problem?.detail)
        assertNull(problem?.errors)
    }

    @Test
    fun parsesTheValidationErrorsArrayShape() {
        val body = """
            {"type":"https://kairon.app/problems/validation","title":"Bad Request","status":400,
             "detail":"Request validation failed.",
             "errors":[{"field":"email","message":"must be a well-formed email address"},
                       {"field":"password","message":"must not be blank"}]}
        """.trimIndent()

        val problem = ProblemDetail.parse(body)

        assertEquals(2, problem?.errors?.size)
        assertEquals("email", problem?.errors?.get(0)?.field)
        assertEquals("must be a well-formed email address", problem?.errors?.get(0)?.message)
        assertEquals("password", problem?.errors?.get(1)?.field)
    }

    @Test
    fun malformedJsonReturnsNullRatherThanThrowing() {
        assertNull(ProblemDetail.parse("not json at all"))
    }

    @Test
    fun emptyOrBlankBodyReturnsNull() {
        assertNull(ProblemDetail.parse(null))
        assertNull(ProblemDetail.parse(""))
        assertNull(ProblemDetail.parse("   "))
    }
}
