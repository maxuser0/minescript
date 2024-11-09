package net.minescript.interpreter;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import java.util.List;
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
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 14
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
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "x",
                    "lineno": 2,
                    "col_offset": 6
                  },
                  "op": {
                    "type": "Mult"
                  },
                  "right": {
                    "type": "Constant",
                    "value": 2,
                    "lineno": 2,
                    "col_offset": 10,
                    "typename": "int"
                  },
                  "lineno": 2,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "y",
                  "lineno": 3,
                  "col_offset": 9
                },
                "lineno": 3,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
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
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 21
                },
                {
                  "type": "arg",
                  "arg": "y1",
                  "annotation": null,
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 25
                },
                {
                  "type": "arg",
                  "arg": "x2",
                  "annotation": null,
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 29
                },
                {
                  "type": "arg",
                  "arg": "y2",
                  "annotation": null,
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 33
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
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "x1",
                    "lineno": 2,
                    "col_offset": 7
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Name",
                    "id": "x2",
                    "lineno": 2,
                    "col_offset": 12
                  },
                  "lineno": 2,
                  "col_offset": 7
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dy",
                    "lineno": 3,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "y1",
                    "lineno": 3,
                    "col_offset": 7
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Name",
                    "id": "y2",
                    "lineno": 3,
                    "col_offset": 12
                  },
                  "lineno": 3,
                  "col_offset": 7
                },
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "d_squared",
                    "lineno": 4,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "BinOp",
                    "left": {
                      "type": "Name",
                      "id": "dx",
                      "lineno": 4,
                      "col_offset": 14
                    },
                    "op": {
                      "type": "Mult"
                    },
                    "right": {
                      "type": "Name",
                      "id": "dx",
                      "lineno": 4,
                      "col_offset": 19
                    },
                    "lineno": 4,
                    "col_offset": 14
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "BinOp",
                    "left": {
                      "type": "Name",
                      "id": "dy",
                      "lineno": 4,
                      "col_offset": 24
                    },
                    "op": {
                      "type": "Mult"
                    },
                    "right": {
                      "type": "Name",
                      "id": "dy",
                      "lineno": 4,
                      "col_offset": 29
                    },
                    "lineno": 4,
                    "col_offset": 24
                  },
                  "lineno": 4,
                  "col_offset": 14
                },
                "type_comment": null,
                "lineno": 4,
                "col_offset": 2
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
                      "lineno": 5,
                      "col_offset": 9
                    },
                    "attr": "sqrt",
                    "lineno": 5,
                    "col_offset": 9
                  },
                  "args": [
                    {
                      "type": "Name",
                      "id": "d_squared",
                      "lineno": 5,
                      "col_offset": 19
                    }
                  ],
                  "keywords": [],
                  "lineno": 5,
                  "col_offset": 9
                },
                "lineno": 5,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
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
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 18
                },
                {
                  "type": "arg",
                  "arg": "p2",
                  "annotation": null,
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 22
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
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p1",
                      "lineno": 2,
                      "col_offset": 7
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 0,
                      "lineno": 2,
                      "col_offset": 10,
                      "typename": "int"
                    },
                    "lineno": 2,
                    "col_offset": 7
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p2",
                      "lineno": 2,
                      "col_offset": 15
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 0,
                      "lineno": 2,
                      "col_offset": 18,
                      "typename": "int"
                    },
                    "lineno": 2,
                    "col_offset": 15
                  },
                  "lineno": 2,
                  "col_offset": 7
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dy",
                    "lineno": 3,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p1",
                      "lineno": 3,
                      "col_offset": 7
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 1,
                      "lineno": 3,
                      "col_offset": 10,
                      "typename": "int"
                    },
                    "lineno": 3,
                    "col_offset": 7
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p2",
                      "lineno": 3,
                      "col_offset": 15
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 1,
                      "lineno": 3,
                      "col_offset": 18,
                      "typename": "int"
                    },
                    "lineno": 3,
                    "col_offset": 15
                  },
                  "lineno": 3,
                  "col_offset": 7
                },
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "dz",
                    "lineno": 4,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p1",
                      "lineno": 4,
                      "col_offset": 7
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 2,
                      "lineno": 4,
                      "col_offset": 10,
                      "typename": "int"
                    },
                    "lineno": 4,
                    "col_offset": 7
                  },
                  "op": {
                    "type": "Sub"
                  },
                  "right": {
                    "type": "Subscript",
                    "value": {
                      "type": "Name",
                      "id": "p2",
                      "lineno": 4,
                      "col_offset": 15
                    },
                    "slice": {
                      "type": "Constant",
                      "value": 2,
                      "lineno": 4,
                      "col_offset": 18,
                      "typename": "int"
                    },
                    "lineno": 4,
                    "col_offset": 15
                  },
                  "lineno": 4,
                  "col_offset": 7
                },
                "type_comment": null,
                "lineno": 4,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "d_squared",
                    "lineno": 5,
                    "col_offset": 2
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
                        "lineno": 5,
                        "col_offset": 14
                      },
                      "op": {
                        "type": "Mult"
                      },
                      "right": {
                        "type": "Name",
                        "id": "dx",
                        "lineno": 5,
                        "col_offset": 19
                      },
                      "lineno": 5,
                      "col_offset": 14
                    },
                    "op": {
                      "type": "Add"
                    },
                    "right": {
                      "type": "BinOp",
                      "left": {
                        "type": "Name",
                        "id": "dy",
                        "lineno": 5,
                        "col_offset": 24
                      },
                      "op": {
                        "type": "Mult"
                      },
                      "right": {
                        "type": "Name",
                        "id": "dy",
                        "lineno": 5,
                        "col_offset": 29
                      },
                      "lineno": 5,
                      "col_offset": 24
                    },
                    "lineno": 5,
                    "col_offset": 14
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "BinOp",
                    "left": {
                      "type": "Name",
                      "id": "dz",
                      "lineno": 5,
                      "col_offset": 34
                    },
                    "op": {
                      "type": "Mult"
                    },
                    "right": {
                      "type": "Name",
                      "id": "dz",
                      "lineno": 5,
                      "col_offset": 39
                    },
                    "lineno": 5,
                    "col_offset": 34
                  },
                  "lineno": 5,
                  "col_offset": 14
                },
                "type_comment": null,
                "lineno": 5,
                "col_offset": 2
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
                      "lineno": 6,
                      "col_offset": 9
                    },
                    "attr": "sqrt",
                    "lineno": 6,
                    "col_offset": 9
                  },
                  "args": [
                    {
                      "type": "Name",
                      "id": "d_squared",
                      "lineno": 6,
                      "col_offset": 19
                    }
                  ],
                  "keywords": [],
                  "lineno": 6,
                  "col_offset": 9
                },
                "lineno": 6,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
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
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 19
                },
                {
                  "type": "arg",
                  "arg": "index",
                  "annotation": null,
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 26
                },
                {
                  "type": "arg",
                  "arg": "value",
                  "annotation": null,
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 33
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
                      "lineno": 2,
                      "col_offset": 2
                    },
                    "slice": {
                      "type": "Name",
                      "id": "index",
                      "lineno": 2,
                      "col_offset": 8
                    },
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "Name",
                  "id": "value",
                  "lineno": 2,
                  "col_offset": 17
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "array",
                  "lineno": 3,
                  "col_offset": 9
                },
                "lineno": 3,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
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
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 17
                },
                {
                  "type": "arg",
                  "arg": "y",
                  "annotation": null,
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 20
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
                    "lineno": 2,
                    "col_offset": 9
                  },
                  "args": [
                    {
                      "type": "Call",
                      "func": {
                        "type": "Name",
                        "id": "float",
                        "lineno": 2,
                        "col_offset": 13
                      },
                      "args": [
                        {
                          "type": "BinOp",
                          "left": {
                            "type": "Name",
                            "id": "x",
                            "lineno": 2,
                            "col_offset": 19
                          },
                          "op": {
                            "type": "Add"
                          },
                          "right": {
                            "type": "Call",
                            "func": {
                              "type": "Name",
                              "id": "str",
                              "lineno": 2,
                              "col_offset": 23
                            },
                            "args": [
                              {
                                "type": "Name",
                                "id": "y",
                                "lineno": 2,
                                "col_offset": 27
                              }
                            ],
                            "keywords": [],
                            "lineno": 2,
                            "col_offset": 23
                          },
                          "lineno": 2,
                          "col_offset": 19
                        }
                      ],
                      "keywords": [],
                      "lineno": 2,
                      "col_offset": 13
                    }
                  ],
                  "keywords": [],
                  "lineno": 2,
                  "col_offset": 9
                },
                "lineno": 2,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
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
                      "lineno": 5,
                      "col_offset": 9
                    },
                    "args": [
                      {
                        "type": "Call",
                        "func": {
                          "type": "Name",
                          "id": "bool",
                          "lineno": 5,
                          "col_offset": 13
                        },
                        "args": [
                          {
                            "type": "Constant",
                            "value": 0.0,
                            "lineno": 5,
                            "col_offset": 18,
                            "typename": "float"
                          }
                        ],
                        "keywords": [],
                        "lineno": 5,
                        "col_offset": 13
                      }
                    ],
                    "keywords": [],
                    "lineno": 5,
                    "col_offset": 9
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "Call",
                    "func": {
                      "type": "Name",
                      "id": "str_int_func",
                      "lineno": 5,
                      "col_offset": 26
                    },
                    "args": [
                      {
                        "type": "Constant",
                        "value": "2.",
                        "lineno": 5,
                        "col_offset": 39,
                        "typename": "str"
                      },
                      {
                        "type": "Constant",
                        "value": 3,
                        "lineno": 5,
                        "col_offset": 45,
                        "typename": "int"
                      }
                    ],
                    "keywords": [],
                    "lineno": 5,
                    "col_offset": 26
                  },
                  "lineno": 5,
                  "col_offset": 9
                },
                "lineno": 5,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 4,
            "col_offset": 0
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
                "lineno": 1,
                "col_offset": 0
              }
            ],
            "value": {
              "type": "Constant",
              "value": 0,
              "lineno": 1,
              "col_offset": 4,
              "typename": "int"
            },
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
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
                ],
                "lineno": 4,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "x",
                    "lineno": 5,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "Name",
                    "id": "x",
                    "lineno": 5,
                    "col_offset": 6
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "Constant",
                    "value": 1,
                    "lineno": 5,
                    "col_offset": 10,
                    "typename": "int"
                  },
                  "lineno": 5,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 5,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 3,
            "col_offset": 0
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
                    "lineno": 8,
                    "col_offset": 2
                  },
                  "args": [],
                  "keywords": [],
                  "lineno": 8,
                  "col_offset": 2
                },
                "lineno": 8,
                "col_offset": 2
              },
              {
                "type": "Expr",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "add_one",
                    "lineno": 9,
                    "col_offset": 2
                  },
                  "args": [],
                  "keywords": [],
                  "lineno": 9,
                  "col_offset": 2
                },
                "lineno": 9,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "x",
                  "lineno": 10,
                  "col_offset": 9
                },
                "lineno": 10,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 7,
            "col_offset": 0
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
                  "type_comment": null,
                  "lineno": 1,
                  "col_offset": 14
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
                  "lineno": 2,
                  "col_offset": 5
                },
                "body": [
                  {
                    "type": "Return",
                    "value": {
                      "type": "BinOp",
                      "left": {
                        "type": "Name",
                        "id": "n",
                        "lineno": 3,
                        "col_offset": 11
                      },
                      "op": {
                        "type": "Mult"
                      },
                      "right": {
                        "type": "Call",
                        "func": {
                          "type": "Name",
                          "id": "factorial",
                          "lineno": 3,
                          "col_offset": 15
                        },
                        "args": [
                          {
                            "type": "BinOp",
                            "left": {
                              "type": "Name",
                              "id": "n",
                              "lineno": 3,
                              "col_offset": 25
                            },
                            "op": {
                              "type": "Sub"
                            },
                            "right": {
                              "type": "Constant",
                              "value": 1,
                              "lineno": 3,
                              "col_offset": 29,
                              "typename": "int"
                            },
                            "lineno": 3,
                            "col_offset": 25
                          }
                        ],
                        "keywords": [],
                        "lineno": 3,
                        "col_offset": 15
                      },
                      "lineno": 3,
                      "col_offset": 11
                    },
                    "lineno": 3,
                    "col_offset": 4
                  }
                ],
                "orelse": [],
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Constant",
                  "value": 1,
                  "lineno": 4,
                  "col_offset": 9,
                  "typename": "int"
                },
                "lineno": 4,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def sqrt9():
        Math = JavaClass("java.lang.Math")
        return Math.sqrt(9)
  */
  private static final String sqrt9JsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "sqrt9",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "Math",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "JavaClass",
                    "lineno": 2,
                    "col_offset": 9
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": "java.lang.Math",
                      "lineno": 2,
                      "col_offset": 19,
                      "typename": "str"
                    }
                  ],
                  "keywords": [],
                  "lineno": 2,
                  "col_offset": 9
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Attribute",
                    "value": {
                      "type": "Name",
                      "id": "Math",
                      "lineno": 3,
                      "col_offset": 9
                    },
                    "attr": "sqrt",
                    "lineno": 3,
                    "col_offset": 9
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": 9,
                      "lineno": 3,
                      "col_offset": 19,
                      "typename": "int"
                    }
                  ],
                  "keywords": [],
                  "lineno": 3,
                  "col_offset": 9
                },
                "lineno": 3,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def nested_func_vars():
        x = "x"
        def bar():
          y = "y"
          def baz():
            z = "z"
            return "baz(" + x + y + z + ")"
          return baz() + ", bar(" + x + y + ")"
        return bar() + ", foo(" + x + ")"
  */
  private static final String nestedFuncVarsJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "nested_func_vars",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "x",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "Constant",
                  "value": "x",
                  "lineno": 2,
                  "col_offset": 6,
                  "typename": "str"
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "FunctionDef",
                "name": "bar",
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
                    "type": "Assign",
                    "targets": [
                      {
                        "type": "Name",
                        "id": "y",
                        "lineno": 4,
                        "col_offset": 4
                      }
                    ],
                    "value": {
                      "type": "Constant",
                      "value": "y",
                      "lineno": 4,
                      "col_offset": 8,
                      "typename": "str"
                    },
                    "type_comment": null,
                    "lineno": 4,
                    "col_offset": 4
                  },
                  {
                    "type": "FunctionDef",
                    "name": "baz",
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
                        "type": "Assign",
                        "targets": [
                          {
                            "type": "Name",
                            "id": "z",
                            "lineno": 6,
                            "col_offset": 6
                          }
                        ],
                        "value": {
                          "type": "Constant",
                          "value": "z",
                          "lineno": 6,
                          "col_offset": 10,
                          "typename": "str"
                        },
                        "type_comment": null,
                        "lineno": 6,
                        "col_offset": 6
                      },
                      {
                        "type": "Return",
                        "value": {
                          "type": "BinOp",
                          "left": {
                            "type": "BinOp",
                            "left": {
                              "type": "BinOp",
                              "left": {
                                "type": "BinOp",
                                "left": {
                                  "type": "Constant",
                                  "value": "baz(",
                                  "lineno": 7,
                                  "col_offset": 13,
                                  "typename": "str"
                                },
                                "op": {
                                  "type": "Add"
                                },
                                "right": {
                                  "type": "Name",
                                  "id": "x",
                                  "lineno": 7,
                                  "col_offset": 22
                                },
                                "lineno": 7,
                                "col_offset": 13
                              },
                              "op": {
                                "type": "Add"
                              },
                              "right": {
                                "type": "Name",
                                "id": "y",
                                "lineno": 7,
                                "col_offset": 26
                              },
                              "lineno": 7,
                              "col_offset": 13
                            },
                            "op": {
                              "type": "Add"
                            },
                            "right": {
                              "type": "Name",
                              "id": "z",
                              "lineno": 7,
                              "col_offset": 30
                            },
                            "lineno": 7,
                            "col_offset": 13
                          },
                          "op": {
                            "type": "Add"
                          },
                          "right": {
                            "type": "Constant",
                            "value": ")",
                            "lineno": 7,
                            "col_offset": 34,
                            "typename": "str"
                          },
                          "lineno": 7,
                          "col_offset": 13
                        },
                        "lineno": 7,
                        "col_offset": 6
                      }
                    ],
                    "decorator_list": [],
                    "returns": null,
                    "type_comment": null,
                    "lineno": 5,
                    "col_offset": 4
                  },
                  {
                    "type": "Return",
                    "value": {
                      "type": "BinOp",
                      "left": {
                        "type": "BinOp",
                        "left": {
                          "type": "BinOp",
                          "left": {
                            "type": "BinOp",
                            "left": {
                              "type": "Call",
                              "func": {
                                "type": "Name",
                                "id": "baz",
                                "lineno": 8,
                                "col_offset": 11
                              },
                              "args": [],
                              "keywords": [],
                              "lineno": 8,
                              "col_offset": 11
                            },
                            "op": {
                              "type": "Add"
                            },
                            "right": {
                              "type": "Constant",
                              "value": ", bar(",
                              "lineno": 8,
                              "col_offset": 19,
                              "typename": "str"
                            },
                            "lineno": 8,
                            "col_offset": 11
                          },
                          "op": {
                            "type": "Add"
                          },
                          "right": {
                            "type": "Name",
                            "id": "x",
                            "lineno": 8,
                            "col_offset": 30
                          },
                          "lineno": 8,
                          "col_offset": 11
                        },
                        "op": {
                          "type": "Add"
                        },
                        "right": {
                          "type": "Name",
                          "id": "y",
                          "lineno": 8,
                          "col_offset": 34
                        },
                        "lineno": 8,
                        "col_offset": 11
                      },
                      "op": {
                        "type": "Add"
                      },
                      "right": {
                        "type": "Constant",
                        "value": ")",
                        "lineno": 8,
                        "col_offset": 38,
                        "typename": "str"
                      },
                      "lineno": 8,
                      "col_offset": 11
                    },
                    "lineno": 8,
                    "col_offset": 4
                  }
                ],
                "decorator_list": [],
                "returns": null,
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "BinOp",
                  "left": {
                    "type": "BinOp",
                    "left": {
                      "type": "BinOp",
                      "left": {
                        "type": "Call",
                        "func": {
                          "type": "Name",
                          "id": "bar",
                          "lineno": 9,
                          "col_offset": 9
                        },
                        "args": [],
                        "keywords": [],
                        "lineno": 9,
                        "col_offset": 9
                      },
                      "op": {
                        "type": "Add"
                      },
                      "right": {
                        "type": "Constant",
                        "value": ", foo(",
                        "lineno": 9,
                        "col_offset": 17,
                        "typename": "str"
                      },
                      "lineno": 9,
                      "col_offset": 9
                    },
                    "op": {
                      "type": "Add"
                    },
                    "right": {
                      "type": "Name",
                      "id": "x",
                      "lineno": 9,
                      "col_offset": 28
                    },
                    "lineno": 9,
                    "col_offset": 9
                  },
                  "op": {
                    "type": "Add"
                  },
                  "right": {
                    "type": "Constant",
                    "value": ")",
                    "lineno": 9,
                    "col_offset": 32,
                    "typename": "str"
                  },
                  "lineno": 9,
                  "col_offset": 9
                },
                "lineno": 9,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def call_sibling_nested_func():
        def bar():
          return "bar"
        def baz():
          return bar()
        return baz()
  */
  private static final String callSiblingNestedFuncJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "call_sibling_nested_func",
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
                "type": "FunctionDef",
                "name": "bar",
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
                      "type": "Constant",
                      "value": "bar",
                      "lineno": 3,
                      "col_offset": 11,
                      "typename": "str"
                    },
                    "lineno": 3,
                    "col_offset": 4
                  }
                ],
                "decorator_list": [],
                "returns": null,
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "FunctionDef",
                "name": "baz",
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
                      "type": "Call",
                      "func": {
                        "type": "Name",
                        "id": "bar",
                        "lineno": 5,
                        "col_offset": 11
                      },
                      "args": [],
                      "keywords": [],
                      "lineno": 5,
                      "col_offset": 11
                    },
                    "lineno": 5,
                    "col_offset": 4
                  }
                ],
                "decorator_list": [],
                "returns": null,
                "type_comment": null,
                "lineno": 4,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "baz",
                    "lineno": 6,
                    "col_offset": 9
                  },
                  "args": [],
                  "keywords": [],
                  "lineno": 6,
                  "col_offset": 9
                },
                "lineno": 6,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def list_ops():
        x = [1, 2, 3]
        x[0] += 100
        x += ["bar"]
        return x
  */
  private static final String listOpsJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "list_ops",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "x",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "List",
                  "elts": [
                    {
                      "type": "Constant",
                      "value": 1,
                      "lineno": 2,
                      "col_offset": 7,
                      "typename": "int"
                    },
                    {
                      "type": "Constant",
                      "value": 2,
                      "lineno": 2,
                      "col_offset": 10,
                      "typename": "int"
                    },
                    {
                      "type": "Constant",
                      "value": 3,
                      "lineno": 2,
                      "col_offset": 13,
                      "typename": "int"
                    }
                  ],
                  "lineno": 2,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "AugAssign",
                "target": {
                  "type": "Subscript",
                  "value": {
                    "type": "Name",
                    "id": "x",
                    "lineno": 3,
                    "col_offset": 2
                  },
                  "slice": {
                    "type": "Constant",
                    "value": 0,
                    "lineno": 3,
                    "col_offset": 4,
                    "typename": "int"
                  },
                  "lineno": 3,
                  "col_offset": 2
                },
                "op": {
                  "type": "Add"
                },
                "value": {
                  "type": "Constant",
                  "value": 100,
                  "lineno": 3,
                  "col_offset": 10,
                  "typename": "int"
                },
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "AugAssign",
                "target": {
                  "type": "Name",
                  "id": "x",
                  "lineno": 4,
                  "col_offset": 2
                },
                "op": {
                  "type": "Add"
                },
                "value": {
                  "type": "List",
                  "elts": [
                    {
                      "type": "Constant",
                      "value": "bar",
                      "lineno": 4,
                      "col_offset": 8,
                      "typename": "str"
                    }
                  ],
                  "lineno": 4,
                  "col_offset": 7
                },
                "lineno": 4,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "x",
                  "lineno": 5,
                  "col_offset": 9
                },
                "lineno": 5,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def ctor_and_method_overloads():
        StringBuilder = JavaClass("java.lang.StringBuilder")
        builder = StringBuilder("This")
        builder.append(" is ")
        builder.append(1)
        builder.append(" test.")
        return builder.toString()
  */
  private static final String ctorAndMethodOverloadsJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "ctor_and_method_overloads",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "StringBuilder",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "JavaClass",
                    "lineno": 2,
                    "col_offset": 18
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": "java.lang.StringBuilder",
                      "lineno": 2,
                      "col_offset": 28,
                      "typename": "str"
                    }
                  ],
                  "keywords": [],
                  "lineno": 2,
                  "col_offset": 18
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "builder",
                    "lineno": 3,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "StringBuilder",
                    "lineno": 3,
                    "col_offset": 12
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": "This",
                      "lineno": 3,
                      "col_offset": 26,
                      "typename": "str"
                    }
                  ],
                  "keywords": [],
                  "lineno": 3,
                  "col_offset": 12
                },
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Expr",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Attribute",
                    "value": {
                      "type": "Name",
                      "id": "builder",
                      "lineno": 4,
                      "col_offset": 2
                    },
                    "attr": "append",
                    "lineno": 4,
                    "col_offset": 2
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": " is ",
                      "lineno": 4,
                      "col_offset": 17,
                      "typename": "str"
                    }
                  ],
                  "keywords": [],
                  "lineno": 4,
                  "col_offset": 2
                },
                "lineno": 4,
                "col_offset": 2
              },
              {
                "type": "Expr",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Attribute",
                    "value": {
                      "type": "Name",
                      "id": "builder",
                      "lineno": 5,
                      "col_offset": 2
                    },
                    "attr": "append",
                    "lineno": 5,
                    "col_offset": 2
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": 1,
                      "lineno": 5,
                      "col_offset": 17,
                      "typename": "int"
                    }
                  ],
                  "keywords": [],
                  "lineno": 5,
                  "col_offset": 2
                },
                "lineno": 5,
                "col_offset": 2
              },
              {
                "type": "Expr",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Attribute",
                    "value": {
                      "type": "Name",
                      "id": "builder",
                      "lineno": 6,
                      "col_offset": 2
                    },
                    "attr": "append",
                    "lineno": 6,
                    "col_offset": 2
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": " test.",
                      "lineno": 6,
                      "col_offset": 17,
                      "typename": "str"
                    }
                  ],
                  "keywords": [],
                  "lineno": 6,
                  "col_offset": 2
                },
                "lineno": 6,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Call",
                  "func": {
                    "type": "Attribute",
                    "value": {
                      "type": "Name",
                      "id": "builder",
                      "lineno": 7,
                      "col_offset": 9
                    },
                    "attr": "toString",
                    "lineno": 7,
                    "col_offset": 9
                  },
                  "args": [],
                  "keywords": [],
                  "lineno": 7,
                  "col_offset": 9
                },
                "lineno": 7,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def range_stop():
        x = []
        for i in range(3):
          x.append(i)
        return x
  */
  private static final String rangeStopJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "range_stop",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "x",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "List",
                  "elts": [],
                  "lineno": 2,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "For",
                "target": {
                  "type": "Name",
                  "id": "i",
                  "lineno": 3,
                  "col_offset": 6
                },
                "iter": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "range",
                    "lineno": 3,
                    "col_offset": 11
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": 3,
                      "lineno": 3,
                      "col_offset": 17,
                      "typename": "int"
                    }
                  ],
                  "keywords": [],
                  "lineno": 3,
                  "col_offset": 11
                },
                "body": [
                  {
                    "type": "Expr",
                    "value": {
                      "type": "Call",
                      "func": {
                        "type": "Attribute",
                        "value": {
                          "type": "Name",
                          "id": "x",
                          "lineno": 4,
                          "col_offset": 4
                        },
                        "attr": "append",
                        "lineno": 4,
                        "col_offset": 4
                      },
                      "args": [
                        {
                          "type": "Name",
                          "id": "i",
                          "lineno": 4,
                          "col_offset": 13
                        }
                      ],
                      "keywords": [],
                      "lineno": 4,
                      "col_offset": 4
                    },
                    "lineno": 4,
                    "col_offset": 4
                  }
                ],
                "orelse": [],
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "x",
                  "lineno": 5,
                  "col_offset": 9
                },
                "lineno": 5,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def range_start_stop_step():
        x = []
        for i in range(4, 10, 2):
          x.append(i)
        return x
  */
  private static final String rangeStartStopStepJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "range_start_stop_step",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "x",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "List",
                  "elts": [],
                  "lineno": 2,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "For",
                "target": {
                  "type": "Name",
                  "id": "i",
                  "lineno": 3,
                  "col_offset": 6
                },
                "iter": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "range",
                    "lineno": 3,
                    "col_offset": 11
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": 4,
                      "lineno": 3,
                      "col_offset": 17,
                      "typename": "int"
                    },
                    {
                      "type": "Constant",
                      "value": 10,
                      "lineno": 3,
                      "col_offset": 20,
                      "typename": "int"
                    },
                    {
                      "type": "Constant",
                      "value": 2,
                      "lineno": 3,
                      "col_offset": 24,
                      "typename": "int"
                    }
                  ],
                  "keywords": [],
                  "lineno": 3,
                  "col_offset": 11
                },
                "body": [
                  {
                    "type": "Expr",
                    "value": {
                      "type": "Call",
                      "func": {
                        "type": "Attribute",
                        "value": {
                          "type": "Name",
                          "id": "x",
                          "lineno": 4,
                          "col_offset": 4
                        },
                        "attr": "append",
                        "lineno": 4,
                        "col_offset": 4
                      },
                      "args": [
                        {
                          "type": "Name",
                          "id": "i",
                          "lineno": 4,
                          "col_offset": 13
                        }
                      ],
                      "keywords": [],
                      "lineno": 4,
                      "col_offset": 4
                    },
                    "lineno": 4,
                    "col_offset": 4
                  }
                ],
                "orelse": [],
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "x",
                  "lineno": 5,
                  "col_offset": 9
                },
                "lineno": 5,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def break_for_loop():
        x = []
        for i in range(10):
          if i >= 2:
            break
          x.append(i)
        return x
  */
  private static final String breakForLoopJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "break_for_loop",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "x",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "List",
                  "elts": [],
                  "lineno": 2,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "For",
                "target": {
                  "type": "Name",
                  "id": "i",
                  "lineno": 3,
                  "col_offset": 6
                },
                "iter": {
                  "type": "Call",
                  "func": {
                    "type": "Name",
                    "id": "range",
                    "lineno": 3,
                    "col_offset": 11
                  },
                  "args": [
                    {
                      "type": "Constant",
                      "value": 10,
                      "lineno": 3,
                      "col_offset": 17,
                      "typename": "int"
                    }
                  ],
                  "keywords": [],
                  "lineno": 3,
                  "col_offset": 11
                },
                "body": [
                  {
                    "type": "If",
                    "test": {
                      "type": "Compare",
                      "left": {
                        "type": "Name",
                        "id": "i",
                        "lineno": 4,
                        "col_offset": 7
                      },
                      "ops": [
                        {
                          "type": "GtE"
                        }
                      ],
                      "comparators": [
                        {
                          "type": "Constant",
                          "value": 2,
                          "lineno": 4,
                          "col_offset": 12,
                          "typename": "int"
                        }
                      ],
                      "lineno": 4,
                      "col_offset": 7
                    },
                    "body": [
                      {
                        "type": "Break",
                        "lineno": 5,
                        "col_offset": 6
                      }
                    ],
                    "orelse": [],
                    "lineno": 4,
                    "col_offset": 4
                  },
                  {
                    "type": "Expr",
                    "value": {
                      "type": "Call",
                      "func": {
                        "type": "Attribute",
                        "value": {
                          "type": "Name",
                          "id": "x",
                          "lineno": 6,
                          "col_offset": 4
                        },
                        "attr": "append",
                        "lineno": 6,
                        "col_offset": 4
                      },
                      "args": [
                        {
                          "type": "Name",
                          "id": "i",
                          "lineno": 6,
                          "col_offset": 13
                        }
                      ],
                      "keywords": [],
                      "lineno": 6,
                      "col_offset": 4
                    },
                    "lineno": 6,
                    "col_offset": 4
                  }
                ],
                "orelse": [],
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "x",
                  "lineno": 7,
                  "col_offset": 9
                },
                "lineno": 7,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def numeric_types():
        def t(x):
          return type(type(x)).getSimpleName()

        return [t(123), t(91234567890), t(123.), t(3.14159265359)]
  */
  private static final String numericTypesJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "numeric_types",
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
                "type": "FunctionDef",
                "name": "t",
                "args": {
                  "type": "arguments",
                  "posonlyargs": [],
                  "args": [
                    {
                      "type": "arg",
                      "arg": "x",
                      "annotation": null,
                      "type_comment": null,
                      "lineno": 2,
                      "col_offset": 8
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
                        "type": "Attribute",
                        "value": {
                          "type": "Call",
                          "func": {
                            "type": "Name",
                            "id": "type",
                            "lineno": 3,
                            "col_offset": 11
                          },
                          "args": [
                            {
                              "type": "Call",
                              "func": {
                                "type": "Name",
                                "id": "type",
                                "lineno": 3,
                                "col_offset": 16
                              },
                              "args": [
                                {
                                  "type": "Name",
                                  "id": "x",
                                  "lineno": 3,
                                  "col_offset": 21
                                }
                              ],
                              "keywords": [],
                              "lineno": 3,
                              "col_offset": 16
                            }
                          ],
                          "keywords": [],
                          "lineno": 3,
                          "col_offset": 11
                        },
                        "attr": "getSimpleName",
                        "lineno": 3,
                        "col_offset": 11
                      },
                      "args": [],
                      "keywords": [],
                      "lineno": 3,
                      "col_offset": 11
                    },
                    "lineno": 3,
                    "col_offset": 4
                  }
                ],
                "decorator_list": [],
                "returns": null,
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "List",
                  "elts": [
                    {
                      "type": "Call",
                      "func": {
                        "type": "Name",
                        "id": "t",
                        "lineno": 5,
                        "col_offset": 10
                      },
                      "args": [
                        {
                          "type": "Constant",
                          "value": 123,
                          "lineno": 5,
                          "col_offset": 12,
                          "typename": "int"
                        }
                      ],
                      "keywords": [],
                      "lineno": 5,
                      "col_offset": 10
                    },
                    {
                      "type": "Call",
                      "func": {
                        "type": "Name",
                        "id": "t",
                        "lineno": 5,
                        "col_offset": 18
                      },
                      "args": [
                        {
                          "type": "Constant",
                          "value": 91234567890,
                          "lineno": 5,
                          "col_offset": 20,
                          "typename": "int"
                        }
                      ],
                      "keywords": [],
                      "lineno": 5,
                      "col_offset": 18
                    },
                    {
                      "type": "Call",
                      "func": {
                        "type": "Name",
                        "id": "t",
                        "lineno": 5,
                        "col_offset": 34
                      },
                      "args": [
                        {
                          "type": "Constant",
                          "value": 123.0,
                          "lineno": 5,
                          "col_offset": 36,
                          "typename": "float"
                        }
                      ],
                      "keywords": [],
                      "lineno": 5,
                      "col_offset": 34
                    },
                    {
                      "type": "Call",
                      "func": {
                        "type": "Name",
                        "id": "t",
                        "lineno": 5,
                        "col_offset": 43
                      },
                      "args": [
                        {
                          "type": "Constant",
                          "value": 3.14159265359,
                          "lineno": 5,
                          "col_offset": 45,
                          "typename": "float"
                        }
                      ],
                      "keywords": [],
                      "lineno": 5,
                      "col_offset": 43
                    }
                  ],
                  "lineno": 5,
                  "col_offset": 9
                },
                "lineno": 5,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def while_loop():
        a = []
        n = 0
        while n < 3:
          a.append(n)
          n += 1
        return a
  */
  private static final String whileLoopJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "while_loop",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "a",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "List",
                  "elts": [],
                  "lineno": 2,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "n",
                    "lineno": 3,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "Constant",
                  "value": 0,
                  "lineno": 3,
                  "col_offset": 6,
                  "typename": "int"
                },
                "type_comment": null,
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "While",
                "test": {
                  "type": "Compare",
                  "left": {
                    "type": "Name",
                    "id": "n",
                    "lineno": 4,
                    "col_offset": 8
                  },
                  "ops": [
                    {
                      "type": "Lt"
                    }
                  ],
                  "comparators": [
                    {
                      "type": "Constant",
                      "value": 3,
                      "lineno": 4,
                      "col_offset": 12,
                      "typename": "int"
                    }
                  ],
                  "lineno": 4,
                  "col_offset": 8
                },
                "body": [
                  {
                    "type": "Expr",
                    "value": {
                      "type": "Call",
                      "func": {
                        "type": "Attribute",
                        "value": {
                          "type": "Name",
                          "id": "a",
                          "lineno": 5,
                          "col_offset": 4
                        },
                        "attr": "append",
                        "lineno": 5,
                        "col_offset": 4
                      },
                      "args": [
                        {
                          "type": "Name",
                          "id": "n",
                          "lineno": 5,
                          "col_offset": 13
                        }
                      ],
                      "keywords": [],
                      "lineno": 5,
                      "col_offset": 4
                    },
                    "lineno": 5,
                    "col_offset": 4
                  },
                  {
                    "type": "AugAssign",
                    "target": {
                      "type": "Name",
                      "id": "n",
                      "lineno": 6,
                      "col_offset": 4
                    },
                    "op": {
                      "type": "Add"
                    },
                    "value": {
                      "type": "Constant",
                      "value": 1,
                      "lineno": 6,
                      "col_offset": 9,
                      "typename": "int"
                    },
                    "lineno": 6,
                    "col_offset": 4
                  }
                ],
                "orelse": [],
                "lineno": 4,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "a",
                  "lineno": 7,
                  "col_offset": 9
                },
                "lineno": 7,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
          }
        ],
        "type_ignores": []
      }
      """;

  /* Generated from Python code:

      def break_while_loop():
        a = []
        while True:
          a.append(1)
          break
        return a
  */
  private static final String breakWhileLoopJsonAst =
      """
      {
        "type": "Module",
        "body": [
          {
            "type": "FunctionDef",
            "name": "break_while_loop",
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
                "type": "Assign",
                "targets": [
                  {
                    "type": "Name",
                    "id": "a",
                    "lineno": 2,
                    "col_offset": 2
                  }
                ],
                "value": {
                  "type": "List",
                  "elts": [],
                  "lineno": 2,
                  "col_offset": 6
                },
                "type_comment": null,
                "lineno": 2,
                "col_offset": 2
              },
              {
                "type": "While",
                "test": {
                  "type": "Constant",
                  "value": true,
                  "lineno": 3,
                  "col_offset": 8,
                  "typename": "bool"
                },
                "body": [
                  {
                    "type": "Expr",
                    "value": {
                      "type": "Call",
                      "func": {
                        "type": "Attribute",
                        "value": {
                          "type": "Name",
                          "id": "a",
                          "lineno": 4,
                          "col_offset": 4
                        },
                        "attr": "append",
                        "lineno": 4,
                        "col_offset": 4
                      },
                      "args": [
                        {
                          "type": "Constant",
                          "value": 1,
                          "lineno": 4,
                          "col_offset": 13,
                          "typename": "int"
                        }
                      ],
                      "keywords": [],
                      "lineno": 4,
                      "col_offset": 4
                    },
                    "lineno": 4,
                    "col_offset": 4
                  },
                  {
                    "type": "Break",
                    "lineno": 5,
                    "col_offset": 4
                  }
                ],
                "orelse": [],
                "lineno": 3,
                "col_offset": 2
              },
              {
                "type": "Return",
                "value": {
                  "type": "Name",
                  "id": "a",
                  "lineno": 6,
                  "col_offset": 9
                },
                "lineno": 6,
                "col_offset": 2
              }
            ],
            "decorator_list": [],
            "returns": null,
            "type_comment": null,
            "lineno": 1,
            "col_offset": 0
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
    var func = interpreter.parse(jsonAst).exec().getFunction("times_two");
    System.out.println(func);

    var output = interpreter.invoke(func, x);
    assertEquals(2 * Math.PI, ((Number) output).doubleValue(), 0.000000001);
  }

  @Test
  public void distanceScalar2() {
    int x1 = 100, y1 = 100, x2 = 103, y2 = 104;

    var jsonAst = JsonParser.parseString(distanceScalar2JsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("distance_scalar2");
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
    var func = interpreter.parse(jsonAst).exec().getFunction("distance_vec3");
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
    var func = interpreter.parse(jsonAst).exec().getFunction("populate_array");
    System.out.println(func);

    var output = interpreter.invoke(func, array, index, value);
    assertArrayEquals(new String[] {"first", null, null}, (String[]) output);
  }

  @Test
  public void typeConversions() {
    var jsonAst = JsonParser.parseString(typeConversionsJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("type_conversions");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals("False2.3", (String) output);
  }

  @Test
  public void incrementGlobal() {
    var jsonAst = JsonParser.parseString(incrementGlobalJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("increment_global");
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
    var func = interpreter.parse(jsonAst).exec().getFunction("factorial");
    System.out.println(func);

    var output = interpreter.invoke(func, 5);
    assertEquals(Integer.valueOf(120), (Integer) output);
  }

  @Test
  public void sqrt9() {
    var jsonAst = JsonParser.parseString(sqrt9JsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("sqrt9");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(3., ((Number) output).doubleValue(), 0.000000001);
  }

  @Test
  public void nestedFuncVars() {
    var jsonAst = JsonParser.parseString(nestedFuncVarsJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("nested_func_vars");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals("baz(xyz), bar(xy), foo(x)", (String) output);
  }

  @Test
  public void callSiblingNestedFunc() {
    var jsonAst = JsonParser.parseString(callSiblingNestedFuncJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("call_sibling_nested_func");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals("bar", (String) output);
  }

  @Test
  public void listOps() {
    var jsonAst = JsonParser.parseString(listOpsJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("list_ops");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(new Interpreter.PyList(List.of(101, 2, 3, "bar")), output);
  }

  @Test
  public void ctorAndMethodOverloads() {
    var jsonAst = JsonParser.parseString(ctorAndMethodOverloadsJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("ctor_and_method_overloads");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals("This is 1 test.", (String) output);
  }

  @Test
  public void rangeStop() {
    var jsonAst = JsonParser.parseString(rangeStopJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("range_stop");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(new Interpreter.PyList(List.of(0, 1, 2)), output);
  }

  @Test
  public void rangeStartStopStep() {
    var jsonAst = JsonParser.parseString(rangeStartStopStepJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("range_start_stop_step");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(new Interpreter.PyList(List.of(4, 6, 8)), output);
  }

  @Test
  public void breakForLoop() {
    var jsonAst = JsonParser.parseString(breakForLoopJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("break_for_loop");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(new Interpreter.PyList(List.of(0, 1)), output);
  }

  @Test
  public void numericTypes() {
    var jsonAst = JsonParser.parseString(numericTypesJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("numeric_types");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(new Interpreter.PyList(List.of("Integer", "Long", "Float", "Double")), output);
  }

  @Test
  public void whileLoop() {
    var jsonAst = JsonParser.parseString(whileLoopJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("while_loop");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(new Interpreter.PyList(List.of(0, 1, 2)), output);
  }

  @Test
  public void breakWhileLoop() {
    var jsonAst = JsonParser.parseString(breakWhileLoopJsonAst);
    var interpreter = new Interpreter();
    var func = interpreter.parse(jsonAst).exec().getFunction("break_while_loop");
    System.out.println(func);

    var output = interpreter.invoke(func);
    assertEquals(new Interpreter.PyList(List.of(1)), output);
  }
}
