/**
 * Copyright (C) 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jliszka.ratemyweekend

import com.github.mustachejava._
import com.google.common.base.Charsets
import com.twitter.finatra._
import com.twitter.mustache._
import java.io._
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class FixedFinatraMustacheFactory(baseTemplatePath: String) extends FinatraMustacheFactory(baseTemplatePath) {
  private def combinePaths(path1: String, path2: String): String = {
    new File(new File(path1), path2).getPath
  }

  override def getReader(resourceName:String): Reader = {
    if (!"development".equals(config.env())) {
      super.getReader(resourceName)
    }
    // In development mode, we look to the local file
    // system and avoid using the classloader which has
    // priority in DefaultMustacheFactory.getReader
    else {
      val fileName = resourceName
      val basePath = combinePaths(config.docRoot(), config.templatePath())
      val file:File = new File(basePath, fileName)

      if (file.exists() && file.isFile()) {
        try {
          new BufferedReader(new InputStreamReader(new FileInputStream(file),
            Charsets.UTF_8));
        } catch {
          case exception:FileNotFoundException =>
            throw new MustacheException("Found Mustache file, could not open: " + file + " at path: " + basePath, exception)
        }
      }
      else {
        throw new MustacheException("Mustache Template '" + resourceName + "' not found at " + file + " at path: " + basePath);
      }
    }
  }
}

object FixedView {
  lazy val mustacheFactory  = new FixedFinatraMustacheFactory(com.twitter.finatra.View.baseTemplatePath)

  mustacheFactory.setObjectHandler(new TwitterObjectHandler)
  mustacheFactory.setExecutorService(Executors.newCachedThreadPool)

  private def combinePaths(path1: String, path2: String): String = {
    new File(new File(path1), path2).getPath
  }
}

abstract class FixedView extends View {

  def template: String

  override def templatePath: String            = FixedView.combinePaths(baseTemplatePath, template)
  override val factory: FixedFinatraMustacheFactory = FixedView.mustacheFactory
  override def mustache: Mustache              = factory.compile(
    new InputStreamReader(FileResolver.getInputStream(templatePath)), template
  )
}
