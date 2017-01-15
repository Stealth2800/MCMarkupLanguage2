/**
 * Copyright 2017 Stealth2800 <http://stealthyone.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stealthyone.mcml2

/**
 * Handles the conversion of an object to a JSON string.
 *
 * @param classes The class(es) supported by this serializer.
 */
abstract class JsonSerializer(vararg val classes: Class<*>) {

    /**
     * Serializes an object to a JSON string.
     *
     * This method should not throw any exceptions, but rather return an error message on failure.
     */
    abstract fun serialize(obj: Any): String

}
