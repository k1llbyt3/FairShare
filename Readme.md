# FairShare 💸

FairShare is a Java Swing desktop application for splitting group expenses and
calculating settlements in a simple and transparent way.

This project was built as a personal learning project to practice Java,
Swing UI development, and advanced object-oriented design patterns.

---

## Features
- Add and manage group members
- Record expenses with payer, amount, description, and payment mode
- Split expenses among selected members
- Record direct payments between members
- Automatically calculate who owes whom
- View a detailed breakdown of expenses
- Copy settlement results to clipboard

---

## Tech Stack
- Java
- Java Swing & AWT
- Java Collections Framework
- **Object-Oriented Programming (OOP)**
    - **Abstraction**: Base `Transaction` class for expenses and payments.
    - **Encapsulation**: Private fields with accessor methods in all models.
    - **Polymorphism**: Unified treatment of financial transactions.
    - **Strategy Pattern**: Pluggable settlement logic via `SettlementStrategy`.

---

## Project Structure
src/
├── app/        # Application entry point
├── ui/         # Swing UI
├── model/      # Data models (Transaction, Expense, Payment, Balance)
├── logic/      # Business logic (SettlementStrategy)
├── components/ # Reusable UI components
└── theme/      # UI theming

---

## How to Run

Compile:
```bash
javac -d bin src/app/FairShareApp.java src/ui/*.java src/components/*.java src/model/*.java src/theme/*.java src/logic/*.java
```

Run:
```bash
java -cp bin app.FairShareApp
```

---

## Why This Project
This project helped me:
- Understand Java Swing layouts and UI behavior
- Apply Core OOP concepts: Abstraction, Encapsulation, Inheritance, and Polymorphism
- Implement Design Patterns (Strategy Pattern)
- Refactor a large class into clean, modular packages
- Handle real-world UI and logic edge cases

---

## Status
Actively maintained as a personal project.
