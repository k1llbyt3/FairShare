# FairShare 💸

FairShare is a Java Swing desktop application for splitting group expenses and
calculating settlements in a simple and transparent way.

This project was built as a personal learning project to practice Java,
Swing UI development, and object-oriented design.

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
- Object-Oriented Programming (OOP)

---

## Project Structure
src/
├── app/ # Application entry point
├── ui/ # Swing UI
├── model/ # Data models
├── components/ # Reusable UI components
└── theme/ # UI theming

---

## How to Run

Compile:
javac -d bin src/app/FairShareApp.java src/ui/.java src/components/.java src/model/.java src/theme/.java

Run:
java -cp bin app.FairShareApp

---

## Why This Project
This project helped me:
- Understand Java Swing layouts and UI behavior
- Apply OOP concepts in a real application
- Refactor a large class into clean, modular packages
- Handle real-world UI and logic edge cases

---

## Status
Actively maintained as a personal project.
