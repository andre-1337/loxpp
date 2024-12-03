# Lox++ Programming Language

Lox++ is a modern, expressive, and versatile programming language built upon the foundation of the Lox language, designed to be efficient and developer-friendly. It introduces numerous features aimed at making development both powerful and intuitive.

## Features

### Core Language Features
- **Tuples**: Collections of elements of any type.
- **Arrays**: Dynamic, zero-based, and resizable collections of elements.
- **Dictionaries**: Key-value storage.
- **Async/Await**: Native support for asynchronous programming.

### Object-Oriented Features
- **Traits**: Code reuse and polymorphism through behavioral contracts.
- **Static Methods**: Class-level methods accessible without instantiating objects.
- **Abstract Methods**: Define method signatures to be implemented by derived classes.

### Error Handling
- **Try/Catch**: Structured error handling for robust applications.
- **Throwable Objects**: Customizable exception handling by throwing objects.

### Iteration and Generics
- **Iterable Objects**: Enable any custom object to be iterated using `for` loops.

### Advanced Functionalities
- **Operator Overloading with Traits**: Define custom behaviors for operators via traits.
- **Standard Library**: A robust standard library providing common utilities for strings, collections, async operations, and more.

## Example Code
* Examples have to be reviewed more thoroughly and show off more of the features.

### Tuples and Arrays
```ts
let tuple = (1, "Hello", true);
print(tuple.1); // "Hello"

let array = [1, 2, 3];
array.insert(4);
println(array); // [1, 2, 3, 4]
```

### Dictionaries
```ts
let dict = { key: "value", another: 42 };
println(dict.key); // "value"
```

### Async/Await
```ts
fn async fetchData() {
  // Simulate async operation
  await std.System.sleep(1000);
  return "Data fetched!";
}

fn async main() {
  let data = await fetchData();
  println(data);
}

main();
```

### Traits and Abstract Methods
```ts
trait Printable {
  abstract fn printSelf();
}

class MyClass with Printable {
  fn printSelf() {
    print("MyClass instance");
  }
}

let obj = MyClass();
obj.printSelf(); // "MyClass instance"
```

### Try/Catch and Throwable Objects
```ts
class MyError with Throwable {
  fn init(msf) { this.msg = msg; }

  fn message() -> self.msg;
}

fn riskyOperation() {
  throw MyError("Something went wrong!");
}

try {
  riskyOperation();
} catch error {
  println(error.message); // "Something went wrong!"
}
```

## Getting Started

1. Clone the repository:
   ```bash
   git clone git@github.com:andre-1337/loxpp.git
   ```
2. Open the project in IntelliJ Idea, and run the Lox++ Runner or build the artifact.

## Contributing

We welcome contributions! Feel free to open issues or pull requests to help improve Lox++.

## License

Lox++ is open-source and available under the MIT License. See the `LICENSE` file for more details.

