function makeAdder(base: integer): integer {
  function inner(value: integer): integer {
    return base + value;
  }
  return inner(5);
}
