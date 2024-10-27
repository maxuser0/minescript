package net.minescript.interpreter;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import org.junit.Test;

public class MainTest {
  // JSON AST strings generated from dump_json_ast.py.
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

  @Test
  public void timesTwo() {
    double x = Math.PI;

    var jsonAst = JsonParser.parseString(timesTwoJsonAst);
    var ast = Interpreter.JsonAstParser.parseStatements(jsonAst);
    System.out.println(ast);

    var context = new Interpreter.Context();
    context.setVariable("x", x);
    ast.eval(context);
    assertEquals(2 * Math.PI, ((Number) context.output()).doubleValue(), 0.000000001);
  }

  @Test
  public void distanceScalar2() {
    int x1 = 100, y1 = 100, x2 = 103, y2 = 104;

    var jsonAst = JsonParser.parseString(distanceScalar2JsonAst);
    var ast = Interpreter.JsonAstParser.parseStatements(jsonAst);
    System.out.println(ast);

    var context = new Interpreter.Context();
    context.setVariable("x1", x1);
    context.setVariable("y1", y1);
    context.setVariable("x2", x2);
    context.setVariable("y2", y2);
    ast.eval(context);
    assertEquals(5., ((Number) context.output()).doubleValue(), 0.00000001);
  }

  @Test
  public void distanceVec3() {
    int[] p1 = new int[] {-1, 5, -1};
    int[] p2 = new int[] {1, 5, 1};

    var jsonAst = JsonParser.parseString(distanceVec3JsonAst);
    var ast = Interpreter.JsonAstParser.parseStatements(jsonAst);
    System.out.println(ast);

    var context = new Interpreter.Context();
    context.setVariable("p1", p1);
    context.setVariable("p2", p2);
    ast.eval(context);
    assertEquals(2 * Math.sqrt(2), ((Number) context.output()).doubleValue(), 0.000000001);
  }

  @Test
  public void populateArray() {
    String[] array = new String[3];
    int index = 0;
    String value = "first";

    var jsonAst = JsonParser.parseString(populateArrayJsonAst);
    var ast = Interpreter.JsonAstParser.parseStatements(jsonAst);
    System.out.println(ast);

    var context = new Interpreter.Context();
    context.setVariable("array", array);
    context.setVariable("index", index);
    context.setVariable("value", value);
    ast.eval(context);
    assertArrayEquals(new String[] {"first", null, null}, (String[]) context.output());
  }
}
