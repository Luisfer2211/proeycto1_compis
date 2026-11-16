function makeAdder(base: integer): integer {
  function inner(value: integer): integer {
    return base + value;
  }
  return inner(5);
}
let result: integer = makeAdder(10);
print(result);
