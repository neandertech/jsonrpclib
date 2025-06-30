import sbt.io.Hash
import sbt.util.FileInfo
import sbt.util.HashFileInfo
import sjsonnew.*

import java.io.File

case class PathRef(path: os.Path)

object PathRef {

  def apply(f: File): PathRef = PathRef(os.Path(f))

  implicit val pathFormat: JsonFormat[PathRef] =
    BasicJsonProtocol.projectFormat[PathRef, HashFileInfo](
      p =>
        if (os.isFile(p.path)) FileInfo.hash(p.path.toIO)
        else
          // If the path is a directory, we get the hashes of all files
          // then hash the concatenation of the hash's bytes.
          FileInfo.hash(
            p.path.toIO,
            Hash(
              os.walk(p.path)
                .map(_.toIO)
                .map(Hash(_))
                .foldLeft(Array.emptyByteArray)(_ ++ _)
            )
          ),
      hash => PathRef(hash.file)
    )
}
