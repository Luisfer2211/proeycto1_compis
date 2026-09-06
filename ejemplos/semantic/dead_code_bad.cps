function stop(): integer {
  return 1;
  let dead: integer = 2;
  print(dead);
}

function stopLoop(): integer {
  while (true) {
    break;
    print("dead");
  }
  return 0;
}

let invalidCall: integer = stop() * stop();
