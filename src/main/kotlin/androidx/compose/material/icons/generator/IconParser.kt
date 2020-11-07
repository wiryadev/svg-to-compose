
/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.material.icons.generator

import androidx.compose.material.icons.generator.vector.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.log10

/**
 * Parser that converts [icon]s into [Vector]s
 */
class IconParser(private val icon: Icon) {

    /**
     * @return a [Vector] representing the provided [icon].
     */
    fun parse(): Vector {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(icon.fileContent.byteInputStream(), null)
            seekToStartTag()
        }

        check(parser.name == "vector") { "The start tag must be <vector>!" }

        val width = rawAsGraphicUnit(parser.getAttributeValue(null, WIDTH))
        val height = rawAsGraphicUnit(parser.getAttributeValue(null, HEIGHT))
        val viewportWidth = parser.getAttributeValue(null, VIEWPORT_WIDTH).toFloat()
        val viewportHeight = parser.getAttributeValue(null, VIEWPORT_HEIGHT).toFloat()

        parser.next()

        val nodes = mutableListOf<VectorNode>()

        var currentGroup: VectorNode.Group? = null

        while (!parser.isAtEnd()) {
            when (parser.eventType) {
                START_TAG -> {
                    when (parser.name) {
                        PATH -> {
                            val pathData = parser.getAttributeValue(
                                null,
                                PATH_DATA
                            )
                            val fillAlpha = parser.getValueAsFloat(FILL_ALPHA)
                            val strokeAlpha = parser.getValueAsFloat(STROKE_ALPHA)
                            val fillType = when (parser.getAttributeValue(null, FILL_TYPE)) {
                                // evenOdd and nonZero are the only supported values here, where
                                // nonZero is the default if no values are defined.
                                EVEN_ODD -> FillType.EvenOdd
                                else -> FillType.NonZero
                            }
                            val strokeCap = parser.getAttributeValue(null, STROKE_LINE_CAP)
                                ?.let { StrokeCap.values().find { strokeCap -> strokeCap.name.equals(it, ignoreCase = true) } }
                            val strokeWidth = rawAsGraphicUnit(parser.getAttributeValue(null, STROKE_WIDTH) ?: "0")
                            val strokeJoin = parser.getAttributeValue(null, STROKE_LINE_JOIN)
                                ?.let { StrokeJoin.values().find { strokeJoin -> strokeJoin.name.equals(it, ignoreCase = true) } }
                            val strokeMiterLimit = parser.getValueAsFloat(STROKE_MITER_LIMIT)

                            val strokeColor = parser.getAttributeValue(null, STROKE_COLOR)
                                ?.toHexColor()

                            val fillColor = parser.getAttributeValue(null, FILL_COLOR)
                                ?.toHexColor()


                            val path = VectorNode.Path(
                                fillColorHex = fillColor,
                                strokeColorHex = strokeColor,
                                strokeAlpha = strokeAlpha ?: 1f,
                                fillAlpha = fillAlpha ?: 1f,
                                strokeLineWidth = strokeWidth,
                                strokeLineCap = strokeCap ?: StrokeCap.Butt,
                                strokeLineJoin = strokeJoin ?: StrokeJoin.Miter,
                                strokeLineMiter = strokeMiterLimit ?: 4.0f,
                                fillType = fillType,
                                nodes = PathParser.parsePathString(pathData)
                            )
                            if (currentGroup != null) {
                                currentGroup.paths.add(path)
                            } else {
                                nodes.add(path)
                            }
                        }
                        // Material icons are simple and don't have nested groups, so this can be simple
                        GROUP -> {
                            val group = VectorNode.Group()
                            currentGroup = group
                            nodes.add(group)
                        }
                        CLIP_PATH -> { /* TODO: b/147418351 - parse clipping paths */
                        }
                    }
                }
            }
            parser.next()
        }

        return Vector(
            width,
            height,
            viewportWidth,
            viewportHeight,
            nodes
        )
    }
}

/**
 * @return the float value for the attribute [name], or null if it couldn't be found
 */
private fun XmlPullParser.getValueAsFloat(name: String) =
    getAttributeValue(null, name)?.toFloatOrNull()

private fun XmlPullParser.seekToStartTag(): XmlPullParser {
    var type = next()
    while (type != START_TAG && type != END_DOCUMENT) {
        // Empty loop
        type = next()
    }
    if (type != START_TAG) {
        throw XmlPullParserException("No start tag found")
    }
    return this
}

private fun XmlPullParser.isAtEnd() =
    eventType == END_DOCUMENT || (depth < 1 && eventType == END_TAG)

private fun String.toHexColor(): String? {
    return removePrefix("#")
        .let {
            if(it.length > 6) it
            else "FF$it"
        }
}

// XML tag names
private const val CLIP_PATH = "clip-path"
private const val GROUP = "group"
private const val PATH = "path"

// Path XML attribute names
private const val PATH_DATA = "android:pathData"
private const val FILL_ALPHA = "android:fillAlpha"
private const val STROKE_ALPHA = "android:strokeAlpha"
private const val FILL_TYPE = "android:fillType"
private const val STROKE_LINE_CAP = "android:strokeLineCap"
private const val STROKE_WIDTH = "android:strokeWidth"
private const val STROKE_LINE_JOIN = "android:strokeLineJoin"
private const val STROKE_MITER_LIMIT = "android:strokeMiterLimit"
private const val STROKE_COLOR = "android:strokeColor"
private const val FILL_COLOR = "android:fillColor"

// Vector XML attribute names
private const val WIDTH = "android:width"
private const val HEIGHT = "android:height"
private const val VIEWPORT_WIDTH = "android:viewportWidth"
private const val VIEWPORT_HEIGHT = "android:viewportHeight"


// XML attribute values
private const val EVEN_ODD = "evenOdd"
