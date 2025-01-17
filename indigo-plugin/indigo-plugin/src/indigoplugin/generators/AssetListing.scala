package indigoplugin.generators

import indigoplugin.IndigoAssets
import scala.annotation.tailrec
import scala.io.AnsiColor._

object AssetListing {

  def generate(
      moduleName: String,
      fullyQualifiedPackage: String,
      indigoAssets: IndigoAssets
  ): os.Path => Seq[os.Path] = outDir => {

    val toSafeName: (String, String) => String =
      indigoAssets.rename.getOrElse(toDefaultSafeName)

    val fileContents: String =
      renderContent(indigoAssets.listAssetFiles, toSafeName)

    val wd = outDir / Generators.OutputDirName

    os.makeDir.all(wd)

    val file = wd / s"$moduleName.scala"

    val contents =
      s"""package $fullyQualifiedPackage
      |
      |import indigo.*
      |
      |// DO NOT EDIT: Generated by Indigo.
      |object $moduleName:
      |
      |${fileContents}
      |
      |""".stripMargin

    os.write.over(file, contents)

    Seq(file)
  }

  def renderContent(paths: List[os.RelPath], toSafeName: (String, String) => String): String =
    (convertPathsToTree _ andThen renderTree(0, toSafeName))(paths)

  def convertPathsToTree(paths: List[os.RelPath]): PathTree =
    PathTree
      .combineAll(
        paths.map { p =>
          PathTree.pathToPathTree(p) match {
            case None        => throw new Exception(s"Could not parse given path: $p")
            case Some(value) => value
          }
        }
      )
      .sorted

  def renderTree(indent: Int, toSafeName: (String, String) => String)(pathTree: PathTree): String =
    pathTree match {
      case PathTree.File(_, _, _) =>
        ""

      case PathTree.Folder(folderName, children) =>
        renderFolderContents(folderName, children, indent, toSafeName)

      case PathTree.Root(children) =>
        renderFolderContents("", children, indent, toSafeName)
    }

  def errorOnDuplicates(
      folderName: String,
      files: List[PathTree.File],
      toSafeName: (String, String) => String
  ): Unit = {
    @tailrec
    def rec(remaining: List[PathTree.File], acc: List[(String, PathTree.File, PathTree.File)]): List[String] =
      remaining match {
        case Nil =>
          acc.map { case (n, a, b) =>
            s"""${GREEN}'$n'${RESET} is the safe name of both ${GREEN}'${a.fullName}'${RESET} and ${GREEN}'${b.fullName}'${RESET}."""
          }

        case e :: es =>
          val errors = es
            .filter(n => toSafeName(n.name, n.extension) == toSafeName(e.name, e.extension))
            .map(n => (toSafeName(e.name, e.extension), e, n))
          rec(es, acc ++ errors)
      }

    val errors = rec(files, Nil)

    if (errors.nonEmpty) {
      val msg =
        s"""
        |${BOLD}${RED}Generated asset name collision${if (errors.length == 1) "" else "s"} in asset folder '${if (
            folderName.isEmpty
          ) "."
          else folderName}'${RESET}
        |${YELLOW}You have one or more conflicting asset names. Please change these names, or move them to separate sub-folders within your assets directory.
        |The following assets would have the same names in your generated asset listings code:${RESET}
        |
        |${errors.mkString("\n")}
        |""".stripMargin

      println(msg)
    } else ()
  }

  def renderFolderContents(
      folderName: String,
      children: List[PathTree],
      indent: Int,
      toSafeName: (String, String) => String
  ): String = {

    val indentSpaces               = List.fill(indent)("  ").mkString
    val indentSpacesNext           = indentSpaces + "  "
    val safeFolderName             = toSafeName(folderName, "")
    val files: List[PathTree.File] = children.collect { case f: PathTree.File => f }

    errorOnDuplicates(folderName, files, toSafeName)

    val renderedFiles: List[(String, String, String)] =
      files
        .map {
          case PathTree.File(name, ext, path) if AudioFileExtensions.contains(ext) =>
            val safeName = toSafeName(name, ext)

            val vals =
              s"""${indentSpacesNext}val ${safeName}: AssetName            = AssetName("${name}.${ext}")
              |${indentSpacesNext}val ${safeName}Play: PlaySound        = PlaySound(${safeName}, Volume.Max)
              |${indentSpacesNext}val ${safeName}SceneAudio: SceneAudio = SceneAudio(SceneAudioSource(BindingKey("${name}.${ext}"), PlaybackPattern.SingleTrackLoop(Track(${safeName}))))""".stripMargin

            val loadable =
              s"""${indentSpacesNext}    AssetType.Audio(${safeName}, AssetPath(baseUrl + "${path}"))"""

            val named =
              s"""${indentSpacesNext}    $safeName"""

            (vals, loadable, named)

          case PathTree.File(name, ext, path) if ImageFileExtensions.contains(ext) =>
            val safeName = toSafeName(name, ext)

            val vals =
              s"""${indentSpacesNext}val ${safeName}: AssetName               = AssetName("${name}.${ext}")
              |${indentSpacesNext}val ${safeName}Material: Material.Bitmap = Material.Bitmap(${safeName})""".stripMargin

            val tag =
              if (safeFolderName.isEmpty) "None"
              else s"""Option(AssetTag("${safeFolderName}"))"""

            val loadable =
              s"""${indentSpacesNext}    AssetType.Image(${safeName}, AssetPath(baseUrl + "${path}"), ${tag})"""

            val named =
              s"""${indentSpacesNext}    $safeName"""

            (vals, loadable, named)

          case PathTree.File(name, ext, path) if FontFileExtensions.contains(ext) =>
            val safeName = toSafeName(name, ext)

            val vals =
              s"""${indentSpacesNext}val ${safeName}: AssetName               = AssetName("${name}_${ext}")
              |${indentSpacesNext}val ${safeName}FontFamily: FontFamily    = FontFamily(${safeName}.toString())"""

            val loadable =
              s"""${indentSpacesNext}    AssetType.Font(${safeName}, AssetPath(baseUrl + "${path}"))"""

            val named =
              s"""${indentSpacesNext}    $safeName"""

            (vals, loadable, named)

          case PathTree.File(name, ext, path) =>
            val safeName = toSafeName(name, ext)

            val vals =
              s"""${indentSpacesNext}val ${safeName}: AssetName = AssetName("${name}.${ext}")"""

            val loadable =
              s"""${indentSpacesNext}    AssetType.Text(${safeName}, AssetPath(baseUrl + "${path}"))"""

            val named =
              s"""${indentSpacesNext}    $safeName"""

            (vals, loadable, named)
        }

    val assetSeq: String =
      if (files.isEmpty) ""
      else
        s"""${renderedFiles.map(_._1).mkString("\n")}
        |
        |${indentSpacesNext}def assetSet(baseUrl: String): Set[AssetType] =
        |${indentSpacesNext}  Set(
        |${renderedFiles.map(_._2).mkString(",\n")}
        |${indentSpacesNext}  )
        |${indentSpacesNext}def assetSet: Set[AssetType] = assetSet("./")
        |
        |${indentSpacesNext}def assetNameSet: Set[AssetName] =
        |${indentSpacesNext}  Set(
        |${renderedFiles.map(_._3).mkString(",\n")}
        |${indentSpacesNext}  )
        |
        |""".stripMargin

    val contents =
      s"""${children.map(renderTree(indent + 1, toSafeName)).mkString}""".stripMargin + assetSeq

    if (safeFolderName.isEmpty) contents
    else
      s"""${indentSpaces}object ${safeFolderName}:
      |${contents}"""
  }

  def toDefaultSafeName: (String, String) => String = { (name: String, _: String) =>
    name.replaceAll("[^a-zA-Z0-9]", "-").split("-").toList.filterNot(_.isEmpty) match {
      case h :: t if h.take(1).matches("[0-9]") => ("_" :: h :: t.map(_.capitalize)).mkString
      case h :: t                               => (h :: t.map(_.capitalize)).mkString
      case l                                    => l.map(_.capitalize).mkString
    }
  }

  val AudioFileExtensions: Set[String] =
    Set(
      "aac",
      "cda",
      "mid",
      "midi",
      "mp3",
      "oga",
      "ogg",
      "opus",
      "wav",
      "weba",
      "flac"
    )

  val ImageFileExtensions: Set[String] =
    Set(
      "apng",
      "avif",
      "gif",
      "jpg",
      "jpeg",
      "jfif",
      "pjpeg",
      "pjp",
      "png",
      "svg",
      "webp",
      "bmp",
      "ico",
      "cur",
      "tif",
      "tiff"
    )

  val FontFileExtensions: Set[String] =
    Set(
      "eot",
      "ttf",
      "woff",
      "woff2"
    )
}
