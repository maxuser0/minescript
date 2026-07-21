# SPDX-FileCopyrightText: © 2026 n-aoH
# SPDX-License-Identifier: GPL-3.0-only

# WARNING: This file is generated from the Minescript jar file. This file will
# be overwritten automatically when Minescript updates to a new version. If you
# make edits to this file, make sure to save a backup copy when upgrading to a
# new version of Minescript.

"""random v5.0 distributed via Minescript jar file

Minescript random module for Pyjinn scripts, modeled after the Python random module.
The Python standard library is licensed under the Python Software Foundation License Agreement
(PSFL).
"""

_Random =  JavaClass("java.util.Random")

random = _Random()


def set_seed(seed: int):
    """
    Sets the seed of the random number generator.
    """
    random.setSeed(seed)


def gauss():
    """
    Returns: the next Gaussian float between -1, 1 with a mean of 0.
    """
    return random.nextGaussian()

def randint(a: int, b: int):
    """
    Return a random integer between a and b, inclusive.
    """
    if a > b:
        raise Exception(" \n[random] : Maximum must be greater than mimumum!")
    
    return a + (random.nextInt((b - a) + 1))

def uniform(a: float, b: float):
    """
    Return a random float between a and b, inclusive.
    """

    if a > b:
        raise Exception(" \n[random] : Maximum must be greater than mimumum! \n")
    
    scale = b-a
    
    return a + (random.nextFloat() * scale)
