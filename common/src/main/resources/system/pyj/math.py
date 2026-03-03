# SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net> & n-aoH
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""math v5.0 distributed via Minescript jar file

Minescript math module for Pyjinn scripts, modeled after the Python math module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""

_Math = JavaClass("java.lang.Math")
_Numbers = JavaClass("net.minescript.common.Numbers")



## Constants

pi = _Math.PI
tau = _Math.TAU
e = _Math.E

## misc

def signum(f: float) -> float:
    """
    Returns the sign of a float.

    f < 0 -> -1
    f = 0 -> 0
    f > 0 -> 1
    """
    return _Math.signum(f)

def fmod(x, y):
  return _Numbers.javaMod(x, y)

## Rounding

def ceil(x: float) -> float:
    """
    Rounds up.
    
    x = 0.7 -> 1
    x = 1.1 -> 2
    x = 3 -> 3
    """
    return _Math.ceil(x)

def floor(x: float) -> float:
    """
    
    Rounds down.

    x = 0.7 -> 0
    x = 1.1 -> 1
    x = 3 -> 3
    """
    return _Math.floor(x)


## Exponents



def sqrt(x: float) -> float:
    """
    Returns the positive square root.
    """
    return _Math.sqrt(x)

def cbrt(x: float) -> float:
    """
    Returns the cube root.
    """
    return _Math.cbrt(x)

def pow(a: float, b: float) -> float:
    """
    Returns (a ^ b)

    Pythonic way of writing: a ** b
    """
    return _Math.pow(a, b)

def log(a: float) -> float:
    """
    Returns the natural logarithm of a.
    """
    return _Math.log(a)


def log10(a: float) -> float:
    """
    Returns the base 10 logarithm of a.
    """
    return _Math.log10(a)






## Trig

def hypot(x: float, y:float) -> float:
    """
    Returns the hypotenuse between two x, y values.
    """
    return _Math.hypot(x, y)

# conversions

def degrees(x: float) -> float:
    """
    Converts degrees to radians.
    """
    return _Math.toDegrees(x)

def radians(x: float) -> float:
    """
    Converts radians to degrees.
    """
    return _Math.toRadians(x)


# sin

def sin(x: float) -> float:
    """
    Returns the  sine of an angle.
    
    """
    return _Math.sin(x)

def asin(x: float) -> float:
    """
    Returns the arc sine of a value.
    """
    return _Math.asin(x)

def sinh(x: float) -> float:
    """
    Returns the hyperbolic sine of a value.
    """
    return _Math.sinh(x)

#cos

def cos(x: float) -> float:
    """
    Returns the cosine of an angle.
    """
    return _Math.cos(x)

def acos(x: float) -> float:
    """
    Returns the arc cosine of a value.
    """
    return _Math.acos(x)

def cosh(x: float) -> float:
    """
    Returns the hyperbolic cosine of a value.
    """
    return _Math.cosh(x)

# tan

def tan(x: float) -> float:
    """
    Returns the tangent of an angle.
    """
    return _Math.tan(x)

def atan(x: float) -> float:
    """
    Returns the arc tangent of a value.
    """
    return _Math.atan(x)

def atan2(x: float,y: float) -> float:
    """
    Gets the angle from the origin to a 2D point.

    Allows you to get the angle from one point to another as well.
    """
    return _Math.atan2(x,y)

def tanh(x: float) -> float:
    """
    Returns the hyperbolic tangent of a value.
    """
    return _Math.tanh(x)
