# SPDX-FileCopyrightText: © 2024-2025 Greg Christiana <maxuser@minescript.net>
# SPDX-License-Identifier: GPL-3.0-only

r"""pathlib v5.0 distributed via Minescript jar file

This library provides a subset of the functionality of the Python pathlib module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""

_Array = JavaClass("java.lang.reflect.Array")
_FileAlreadyExistsException = JavaClass("java.nio.file.FileAlreadyExistsException")
_FileAttribute = JavaClass("java.nio.file.attribute.FileAttribute")
_Files = JavaClass("java.nio.file.Files")
_JavaPath = JavaClass("java.nio.file.Path")
_LinkOption = JavaClass("java.nio.file.LinkOption")
_String = JavaClass("java.lang.String")

class Path:
  def __init__(self, path: str):
    self.path = path
    self.java_path = _JavaPath.of(path, _Array.newInstance(type(_String), 0))

  # TODO(maxuser): Support __str__() method for conversion to string.

  def mkdir(self, exist_ok=True):
    attrs = _Array.newInstance(type(_FileAttribute), 0)
    if exist_ok:
      _Files.createDirectories(self.java_path, attrs)
    else:
      if _Files.exists(self.java_path):
        raise _FileAlreadyExistsException("Directory already exists: " + self.path)
      _Files.createDirectories(self.java_path, attrs)

  def joinpath(self, *elements) -> "Path":
    return Path(self.path + '/' + '/'.join(elements))
  
  def exists(self) -> bool:
    options = _Array.newInstance(type(_LinkOption), 0)
    return _Files.exists(self.java_path, options)

