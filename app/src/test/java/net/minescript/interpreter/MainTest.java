package net.minescript.interpreter;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import org.junit.Test;

public class MainTest {
  /* Generated from Python code:

      def times_two(x):
        y = x * 2
        return y
  */
  private static final String timesTwoJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "times_two",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [
                {
                  "type": "arg",
                  "arg": "x",
                  "annotation": null,
                  "type_comment": null
                }
              ],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "y",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "x",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "op": {
                    "type": "Mult"
                  },
                  "right": {
                    "type": "Constant",
                    "value": 2,
                    "kind": null
                  }
                },
                "type_comment": null
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "y",
                  "ctx": {
                    "type": "Load"
                  }
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def distance_scalar2(x1, y1, x2, y2):
        dx = x1 - x2
        dy = y1 - y2
        d_squared = dx * dx + dy * dy
        return math.sqrt(d_squared)
  */
  private static final String distanceScalar2JsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "distance_scalar2",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [
                {
                  "type": "arg",
                  "arg": "x1",
                  "annotation": null,
                  "type_comment": null
                },
                {
                  "type": "arg",
                  "arg": "y1",
                  "annotation": null,
                  "type_comment": null
                },
                {
                  "type": "arg",
                  "arg": "x2",
                  "annotation": null,
                  "type_comment": null
                },
                {
                  "type": "arg",
                  "arg": "y2",
                  "annotation": null,
                  "type_comment": null
                }
              ],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dx",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "x1",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Name",
                    "id": "x2",
                    "ctx": {
                      "type": "Load"
                    }
                  }
                },
                "type_comment": null
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dy",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "y1",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Name",
                    "id": "y2",
                    "ctx": {
                      "type": "Load"
                    }
                  }
                },
                "type_comment": null
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "d_squared",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "BinOp",
                    "left": {
                      "type": "Name",
                      "id": "dx",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "op": {
                      "type": "Mult"
                    },
                    "right": {
                      "type": "Name",
                      "id": "dx",
                      "ctx": {
                        "type": "Load"
                      }
                    }
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "BinOp",
                    "left": {
                      "type": "Name",
                      "id": "dy",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "op": {
                      "type": "Mult"
                    },
                    "right": {
                      "type": "Name",
                      "id": "dy",
                      "ctx": {
                        "type": "Load"
                      }
                    }
                  }
                },
                "type_comment": null
              },
              {
                "type": "Return",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Attribute",
                    "value": {
                      "type": "Name",
                      "id": "math",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "attr": "sqrt",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "args": [
                    {
                      "type": "Name",
                      "id": "d_squared",
                      "ctx": {
                        "type": "Load"
                      }
                    }
                  ],
                  "keywords": []
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def distance_vec3(p1, p2):
        dx = p1[0] - p2[0]
        dy = p1[1] - p2[1]
        dz = p1[2] - p2[2]
        d_squared = dx * dx + dy * dy + dz * dz
        return math.sqrt(d_squared)
  */
  private static final String distanceVec3JsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "distance_vec3",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [
                {
                  "type": "arg",
                  "arg": "p1",
                  "annotation": null,
                  "type_comment": null
                },
                {
                  "type": "arg",
                  "arg": "p2",
                  "annotation": null,
                  "type_comment": null
                }
              ],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dx",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p1",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 0,
                      "kind": null
                    },
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p2",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 0,
                      "kind": null
                    },
                    "ctx": {
                      "type": "Load"
                    }
                  }
                },
                "type_comment": null
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dy",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p1",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 1,
                      "kind": null
                    },
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p2",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 1,
                      "kind": null
                    },
                    "ctx": {
                      "type": "Load"
                    }
                  }
                },
                "type_comment": null
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dz",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p1",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 2,
                      "kind": null
                    },
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p2",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 2,
                      "kind": null
                    },
                    "ctx": {
                      "type": "Load"
                    }
                  }
                },
                "type_comment": null
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "d_squared",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "BinOp",
                    "left": {
                      "type": "BinOp",
                      "left": {
                        "type": "Name",
                        "id": "dx",
                        "ctx": {
                          "type": "Load"
                        }
                      },
                      "op": {
                        "type": "Mult"
                      },
                      "right": {
                        "type": "Name",
                        "id": "dx",
                        "ctx": {
                          "type": "Load"
                        }
                      }
                    },
                    "op": {
                      "type": "Add"
                    },
                    "right": {
                      "type": "BinOp",
                      "left": {
                        "type": "Name",
                        "id": "dy",
                        "ctx": {
                          "type": "Load"
                        }
                      },
                      "op": {
                        "type": "Mult"
                      },
                      "right": {
                        "type": "Name",
                        "id": "dy",
                        "ctx": {
                          "type": "Load"
                        }
                      }
                    }
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "BinOp",
                    "left": {
                      "type": "Name",
                      "id": "dz",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "op": {
                      "type": "Mult"
                    },
                    "right": {
                      "type": "Name",
                      "id": "dz",
                      "ctx": {
                        "type": "Load"
                      }
                    }
                  }
                },
                "type_comment": null
              },
              {
                "type": "Return",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Attribute",
                    "value": {
                      "type": "Name",
                      "id": "math",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "attr": "sqrt",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "args": [
                    {
                      "type": "Name",
                      "id": "d_squared",
                      "ctx": {
                        "type": "Load"
                      }
                    }
                  ],
                  "keywords": []
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def populate_array(array, index, value):
        array[index] = value
        return array
  */
  private static final String populateArrayJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "populate_array",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [
                {
                  "type": "arg",
                  "arg": "array",
                  "annotation": null,
                  "type_comment": null
                },
                {
                  "type": "arg",
                  "arg": "index",
                  "annotation": null,
                  "type_comment": null
                },
                {
                  "type": "arg",
                  "arg": "value",
                  "annotation": null,
                  "type_comment": null
                }
              ],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "array",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "slice": {
                      "type": "Name",
                      "id": "index",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "Name",
                  "id": "value",
                  "ctx": {
                    "type": "Load"
                  }
                },
                "type_comment": null
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "array",
                  "ctx": {
                    "type": "Load"
                  }
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def str_int_func(x, y):
        return str(float(x + str(y)))

      def type_conversions():
        return str(bool(0.0)) + str_int_func("2.", 3)
  */
  private static final String typeConversionsJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "str_int_func",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [
                {
                  "type": "arg",
                  "arg": "x",
                  "annotation": null,
                  "type_comment": null
                },
                {
                  "type": "arg",
                  "arg": "y",
                  "annotation": null,
                  "type_comment": null
                }
              ],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Return",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "str",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "args": [
                    {
                      "type": "Call",
                      "func": {
                        "type": "Name",
                        "id": "float",
                        "ctx": {
                          "type": "Load"
                        }
                      },
                      "args": [
                        {
                          "type": "BinOp",
                          "left": {
                            "type": "Name",
                            "id": "x",
                            "ctx": {
                              "type": "Load"
                            }
                          },
                          "op": {
                            "type": "Add"
                          },
                          "right": {
                            "type": "Call",
                            "func": {
                              "type": "Name",
                              "id": "str",
                              "ctx": {
                                "type": "Load"
                              }
                            },
                            "args": [
                              {
                                "type": "Name",
                                "id": "y",
                                "ctx": {
                                  "type": "Load"
                                }
                              }
                            ],
                            "keywords": []
                          }
                        }
                      ],
                      "keywords": []
                    }
                  ],
                  "keywords": []
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          },
          {
            "type": "FunctionDef",
            "name": "type_conversions",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Return",
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Call",
                    "func": {
                      "type": "Name",
                      "id": "str",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "args": [
                      {
                        "type": "Call",
                        "func": {
                          "type": "Name",
                          "id": "bool",
                          "ctx": {
                            "type": "Load"
                          }
                        },
                        "args": [
                          {
                            "type": "Constant",
                            "value": 0.0,
                            "kind": null
                          }
                        ],
                        "keywords": []
                      }
                    ],
                    "keywords": []
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "Call",
                    "func": {
                      "type": "Name",
                      "id": "str_int_func",
                      "ctx": {
                        "type": "Load"
                      }
                    },
                    "args": [
                      {
                        "type": "Constant",
                        "value": "2.",
                        "kind": null
                      },
                      {
                        "type": "Constant",
                        "value": 3,
                        "kind": null
                      }
                    ],
                    "keywords": []
                  }
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      x = 0

      def add_one():
        global x
        x = x + 1

      def increment_global():
        add_one()
        add_one()
        return x
  */
  private static final String incrementGlobalJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "Assign",
            "targets": [
              {
                "type": "Name",
                "id": "x",
                "ctx": {
                  "type": "Store"
                }
              }
            ],
            "value": {
              "type": "Constant",
              "value": 0,
              "kind": null
            },
            "type_comment": null
          },
          {
            "type": "FunctionDef",
            "name": "add_one",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Global",
                "names": [
                  "x"
                ]
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "x",
                    "ctx": {
                      "type": "Store"
                    }
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "x",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "Constant",
                    "value": 1,
                    "kind": null
                  }
                },
                "type_comment": null
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          },
          {
            "type": "FunctionDef",
            "name": "increment_global",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "Expr",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "add_one",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "args": [],
                  "keywords": []
                }
              },
              {
                "type": "Expr",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "add_one",
                    "ctx": {
                      "type": "Load"
                    }
                  },
                  "args": [],
                  "keywords": []
                }
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "x",
                  "ctx": {
                    "type": "Load"
                  }
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def factorial(n):
        if n:
          return n * factorial(n - 1)
        return 1
  */
  private static final String factorialJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "factorial",
            "args": {
              "type": "arguments",
              "posonlyargs": [],
              "args": [
                {
                  "type": "arg",
                  "arg": "n",
                  "annotation": null,
                  "type_comment": null
                }
              ],
              "vararg": null,
              "kwonlyargs": [],
              "kw_defaults": [],
              "kwarg": null,
              "defaults": []
            },
            "body": [
              {
                "type": "If",
                "test": {
                  "type": "Name",
                  "id": "n",
                  "ctx": {
                    "type": "Load"
                  }
                },
                "body": [
                  {
                    "type": "Return",
                    "value": {
                      "type": "BinOp",
                      "left": {
                        "type": "Name",
                        "id": "n",
                        "ctx": {
                          "type": "Load"
                        }
                      },
                      "op": {
                        "type": "Mult"
                      },
                      "right": {
                        "type": "Call",
                        "func": {
                          "type": "Name",
                          "id": "factorial",
                          "ctx": {
                            "type": "Load"
                          }
                        },
                        "args": [
                          {
                            "type": "BinOp",
                            "left": {
                              "type": "Name",
                              "id": "n",
                              "ctx": {
                                "type": "Load"
                              }
                            },
                            "op": {
                              "type": "Sub"
                            },
                            "right": {
                              "type": "Constant",
                              "value": 1,
                              "kind": null
                            }
                          }
                        ],
                        "keywords": []
                      }
                    }
                  }
                ],
                "orelse": []
              },
              {
                "type": "Return",
                "value": {
                  "type": "Constant",
                  "value": 1,
                  "kind": null
                }
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null
          }
        ],
        "type_ignores": []
      }
      """;

  @Test
  public void timesTwo() {
    double x = Math.PI;

    var jsonAst = JsonParser.parseString(timesTwoJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).getFunction("times_two");
    System.out.println(func);

    var output = interpreter.invoke(func, x);
    assertEquals(2 * Math.PI, ((Number) output).doubleValue(), 0.000000001);
  }

  @Test
  public void distanceScalar2() {
    int x1 = 100, y1 = 100, x2 = 103, y2 = 104;

    var jsonAst = JsonParser.parseString(distanceScalar2JsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).getFunction("distance_scalar2");
    System.out.println(func);

    var output = interpreter.invoke(func, x1, y1, x2, y2);
    assertEquals(5., ((Number) output).doubleValue(), 0.00000001);
  }

  @Test
  public void distanceVec3() {
    int[] p1 = new int[] {-1, 5, -1};
    int[] p2 = new int[] {1, 5, 1};

    var jsonAst = JsonParser.parseString(distanceVec3JsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).getFunction("distance_vec3");
    System.out.println(func);

    var output = interpreter.invoke(func, p1, p2);
    assertEquals(2 * Math.sqrt(2), ((Number) output).doubleValue(), 0.000000001);
  }

  @Test
  public void populateArray() {
    String[] array = new String[3];
    int index = 0;
    String value = "first";

    var jsonAst = JsonParser.parseString(populateArrayJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).getFunction("populate_array");
    System.out.println(func);

    var output = interpreter.invoke(func, array, index, value);
    assertArrayEquals(new String[] {"first", null, null}, (String[]) output);
  }

  @Test
  public void typeConversions() {
    var jsonAst = JsonParser.parseString(typeConversionsJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).getFunction("type_conversions");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals("false2.3", (String) output);
  }

  @Test
  public void incrementGlobal() {
    var jsonAst = JsonParser.parseString(incrementGlobalJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).getFunction("increment_global");
    System.out.println(func);

    // Execute global statement to define global var: `x = 0`
    interpreter.exec();

    var output = interpreter.invoke(func);
    assertEquals(Integer.valueOf(2), (Integer) output);
  }

  @Test
  public void factorial() {
    var jsonAst = JsonParser.parseString(factorialJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).getFunction("factorial");
    System.out.println(func);

    var output = interpreter.invoke(func, 5);
    assertEquals(Integer.valueOf(120), (Integer) output);
  }
}
