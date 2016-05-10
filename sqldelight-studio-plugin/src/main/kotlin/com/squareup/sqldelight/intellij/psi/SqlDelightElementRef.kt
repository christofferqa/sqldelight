/*
 * Copyright (C) 2016 Square, Inc.
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
package com.squareup.sqldelight.intellij.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.squareup.sqldelight.SqliteLexer
import com.squareup.sqldelight.intellij.lang.SqlDelightFileViewProvider
import com.squareup.sqldelight.intellij.lang.SqliteFile
import com.squareup.sqldelight.intellij.lang.SqliteTokenTypes
import com.squareup.sqldelight.intellij.util.SqlitePsiUtils
import com.squareup.sqldelight.intellij.util.doRename
import com.squareup.sqldelight.intellij.util.findUsages
import com.squareup.sqldelight.intellij.util.parentOfType
import com.squareup.sqldelight.intellij.util.containingParse
import com.squareup.sqldelight.types.ResolutionError
import com.squareup.sqldelight.types.Resolver
import com.squareup.sqldelight.validation.SqlDelightValidator

class SqlDelightElementRef(idNode: IdentifierElement, private val ruleName: String)
: PsiReferenceBase<IdentifierElement>(idNode, TextRange(0, ruleName.length)) {
  val hasJavaReferences = idNode.parentOfType<SqliteElement.ColumnNameElement>() != null ||
      idNode.parentOfType<SqliteElement.SqlStmtNameElement>() != null

  /**
   *  @see SqlDelightCompletionContributor
   */
  override fun getVariants() = emptyArray<Any>()

  override fun resolve(): PsiElement? {
    var result: PsiElement? = null
    (element.containingFile as SqliteFile).parseThen({ parsed ->
      val elementFound = parsed.sql_stmt_list().sql_stmt()
          .filter { it.start.startIndex < element.textOffset && it.stop.stopIndex > element.textOffset }
          .flatMap {
            try {
              SqlDelightValidator().validate(it, Resolver(SqlDelightFileViewProvider.symbolTable,
                  elementToFind = element.textOffset))
            } catch (e: Throwable) {
              emptyList<ResolutionError>()
            }
          }
          .filterIsInstance<ResolutionError.ElementFound>()
          .firstOrNull()
      if (elementFound != null) {
        result = SqliteFile.parseTreeMap.getKeysByValue(
            elementFound.originatingElement.containingParse())!!.first().findElementAt(
            elementFound.originatingElement.start.startIndex)
      }
    })
    return result
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    if (hasJavaReferences) {
      val file = myElement.containingFile as SqliteFile

      val usageInfo = myElement.findUsages(newElementName)
      myElement.doRename(newElementName, usageInfo, file, null)

      return myElement
    } else {
      myElement.replace(SqlitePsiUtils.createLeafFromText(element.project, myElement.context,
          newElementName, SqliteTokenTypes.TOKEN_ELEMENT_TYPES[SqliteLexer.IDENTIFIER]))
      return myElement
    }
  }
}
