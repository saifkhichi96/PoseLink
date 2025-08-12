/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.saifkhichi.poselink.gles

import android.opengl.GLES20
import android.opengl.Matrix
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Some OpenGL utility functions.
 */
object GlUtil {
    const val TAG: String = "Grafika"

    /**
     * Identity matrix for general use.  Don't modify or life will get weird.
     */
    val IDENTITY_MATRIX: FloatArray = FloatArray(16)

    init {
        Matrix.setIdentityM(IDENTITY_MATRIX, 0)
    }

    private const val SIZEOF_FLOAT = 4


    /**
     * Creates a new program from the supplied vertex and fragment shaders.
     *
     * @return A handle to the program, or 0 on failure.
     */
    fun createProgram(vertexSource: String?, fragmentSource: String?): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            return 0
        }

        var program = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")
        if (program == 0) {
            Timber.e("Could not create program")
        }
        GLES20.glAttachShader(program, vertexShader)
        checkGlError("glAttachShader")
        GLES20.glAttachShader(program, pixelShader)
        checkGlError("glAttachShader")
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Timber.e("Could not link program: ")
            Timber.e(GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }
        return program
    }

    /**
     * Compiles the provided shader source.
     *
     * @return A handle to the shader, or 0 on failure.
     */
    fun loadShader(shaderType: Int, source: String?): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Timber.e(
                "Could not compile shader %d: %s",
                shaderType, GLES20.glGetShaderInfoLog(shader)
            )
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    fun checkGlError(op: String?) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = op + ": glError 0x" + Integer.toHexString(error)
            Timber.e(msg)
            throw RuntimeException(msg)
        }
    }

    /**
     * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
     * could not be found, but does not set the GL error.
     *
     *
     * Throws a RuntimeException if the location is invalid.
     */
    fun checkLocation(location: Int, label: String?) {
        if (location < 0) {
            throw RuntimeException("Unable to locate '$label' in program")
        }
    }

    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        val bb = ByteBuffer.allocateDirect(coords.size * SIZEOF_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.position(0)
        return fb
    }
}
