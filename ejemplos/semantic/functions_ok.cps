function add(a: integer, b: integer): integer {
  return a + b;
}

function greet(name: string): string {
  return "Hello " + name;
}

let total: integer = add(1, 2);
let message: string = greet("World");

function factorial(n: integer): integer {
  if (n <= 1) {
    return 1;
  }
  return n * factorial(n - 1);
}

function makeAdder(base: integer): integer {
  function inner(value: integer): integer {
    return base + value;
  }
  return inner(5);
}
