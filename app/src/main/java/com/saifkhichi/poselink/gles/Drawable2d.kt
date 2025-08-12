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

import java.nio.FloatBuffer

/**
 * Base class for stuff we like to draw.
 */
class Drawable2d(shape: Prefab) {
    /**
     * Returns the array of vertices.
     *
     *
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    var vertexArray: FloatBuffer? = null

    /**
     * Returns the array of texture coordinates.
     *
     *
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    var texCoordArray: FloatBuffer? = null

    /**
     * Returns the number of vertices stored in the vertex array.
     */
    var vertexCount: Int = 0

    /**
     * Returns the number of position coordinates per vertex.  This will be 2 or 3.
     */
    var coordsPerVertex: Int = 0

    /**
     * Returns the width, in bytes, of the data for each vertex.
     */
    var vertexStride: Int = 0

    /**
     * Returns the width, in bytes, of the data for each texture coordinate.
     */
    val texCoordStride: Int
    private val mPrefab: Prefab?

    /**
     * Enum values for constructor.
     */
    enum class Prefab {
        TRIANGLE, RECTANGLE, FULL_RECTANGLE
    }

    /**
     * Prepares a drawable from a "pre-fabricated" shape definition.
     *
     *
     * Does no EGL/GL operations, so this can be done at any time.
     */
    init {
        when (shape) {
            Prefab.TRIANGLE -> {
                this.vertexArray = TRIANGLE_BUF
                this.texCoordArray = TRIANGLE_TEX_BUF
                this.coordsPerVertex = 2
                this.vertexStride = this.coordsPerVertex * SIZEOF_FLOAT
                this.vertexCount = TRIANGLE_COORDS.size / this.coordsPerVertex
            }

            Prefab.RECTANGLE -> {
                this.vertexArray = RECTANGLE_BUF
                this.texCoordArray = RECTANGLE_TEX_BUF
                this.coordsPerVertex = 2
                this.vertexStride = this.coordsPerVertex * SIZEOF_FLOAT
                this.vertexCount = RECTANGLE_COORDS.size / this.coordsPerVertex
            }

            Prefab.FULL_RECTANGLE -> {
                this.vertexArray = FULL_RECTANGLE_BUF
                this.texCoordArray = FULL_RECTANGLE_TEX_BUF
                this.coordsPerVertex = 2
                this.vertexStride = this.coordsPerVertex * SIZEOF_FLOAT
                this.vertexCount = FULL_RECTANGLE_COORDS.size / this.coordsPerVertex
            }
        }
        this.texCoordStride = 2 * SIZEOF_FLOAT
        mPrefab = shape
    }

    override fun toString(): String {
        if (mPrefab != null) {
            return "[Drawable2d: $mPrefab]"
        } else {
            return "[Drawable2d: ...]"
        }
    }

    companion object {
        private const val SIZEOF_FLOAT = 4

        /**
         * Simple equilateral triangle (1.0 per side).  Centered on (0,0).
         */
        private val TRIANGLE_COORDS: FloatArray = floatArrayOf(
            0.0f, 0.57735026f,  // 0 top
            -0.5f, -0.28867513f,  // 1 bottom left
            0.5f, -0.28867513f // 2 bottom right
        )
        private val TRIANGLE_TEX_COORDS: FloatArray = floatArrayOf(
            0.5f, 0.0f,  // 0 top center
            0.0f, 1.0f,  // 1 bottom left
            1.0f, 1.0f,  // 2 bottom right
        )
        private val TRIANGLE_BUF: FloatBuffer = GlUtil.createFloatBuffer(TRIANGLE_COORDS)
        private val TRIANGLE_TEX_BUF: FloatBuffer = GlUtil.createFloatBuffer(TRIANGLE_TEX_COORDS)

        /**
         * Simple square, specified as a triangle strip.  The square is centered on (0,0) and has
         * a size of 1x1.
         *
         *
         * Triangles are 0-1-2 and 2-1-3 (counter-clockwise winding).
         */
        private val RECTANGLE_COORDS: FloatArray = floatArrayOf(
            -0.5f, -0.5f,  // 0 bottom left
            0.5f, -0.5f,  // 1 bottom right
            -0.5f, 0.5f,  // 2 top left
            0.5f, 0.5f,  // 3 top right
        )
        private val RECTANGLE_TEX_COORDS: FloatArray = floatArrayOf(
            0.0f, 1.0f,  // 0 bottom left
            1.0f, 1.0f,  // 1 bottom right
            0.0f, 0.0f,  // 2 top left
            1.0f, 0.0f // 3 top right
        )
        private val RECTANGLE_BUF: FloatBuffer = GlUtil.createFloatBuffer(RECTANGLE_COORDS)
        private val RECTANGLE_TEX_BUF: FloatBuffer = GlUtil.createFloatBuffer(RECTANGLE_TEX_COORDS)

        /**
         * A "full" square, extending from -1 to +1 in both dimensions.  When the model/view/projection
         * matrix is identity, this will exactly cover the viewport.
         *
         *
         * The texture coordinates are Y-inverted relative to RECTANGLE.  (This seems to work out
         * right with external textures from SurfaceTexture.)
         */
        private val FULL_RECTANGLE_COORDS: FloatArray = floatArrayOf(
            -1.0f, -1.0f,  // 0 bottom left
            1.0f, -1.0f,  // 1 bottom right
            -1.0f, 1.0f,  // 2 top left
            1.0f, 1.0f,  // 3 top right
        )
        private val FULL_RECTANGLE_TEX_COORDS: FloatArray = floatArrayOf(
            0.0f, 0.0f,  // 0 bottom left
            1.0f, 0.0f,  // 1 bottom right
            0.0f, 1.0f,  // 2 top left
            1.0f, 1.0f // 3 top right
        )
        private val FULL_RECTANGLE_BUF: FloatBuffer =
            GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS)
        private val FULL_RECTANGLE_TEX_BUF: FloatBuffer = GlUtil.createFloatBuffer(
            FULL_RECTANGLE_TEX_COORDS
        )
    }
}
