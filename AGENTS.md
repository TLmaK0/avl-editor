# AVL Editor

This project is an editor for CRRCsim, a radio-controlled aircraft simulator, and integrates with AVL for aerodynamic analysis.
It allows users to modify aircraft parameters and configurations for simulation.
Developed in Java and Scala, it provides a GUI for streamlined editing and analysis.

## Building and Running

### Compilation

To compile the project, run the following command:

```bash
sbt compile
```

### Running the application

Use the provided script to start the application:

```bash
./run.sh
```

This script will:
- Kill any existing instance of the application
- Start the application in background mode

The application will start in full-screen mode.

**Note for agents:** When running from a CLI agent, use background execution to avoid blocking:
```bash
./run.sh &
```

### Commit Message Guidelines

Commit messages should be concise, clear, and a maximum of two lines. The first line should be a brief summary, and the second line can optionally provide more context if needed.

### Git Push Policy

**IMPORTANT:** Never push to remote without explicit user permission. Always ask before running `git push`.

### Recent UI Changes

- The button to add a new section to a surface has been changed to a `+` button for a more intuitive user experience.

## Feature Planning

### PLAN.md

**IMPORTANT:** Before starting any task, the agent MUST first create or update the plan in `PLAN.md`. No implementation work should begin until the plan is written and reviewed.

The project uses a local `PLAN.md` file (not tracked in git) to maintain context across sessions for the current feature being developed.

**Usage:**
- Plans must be written in English
- This file should be rewritten/reset when starting a new feature
- It contains the feature description, current status, next steps, and relevant context
- Helps the agent continue work after context cleanup
- Should include task lists, important decisions, and files being modified

## Engineering Policy

- Never ship "quick patches" as final fixes.
- Always solve the root cause, even if the implementation takes longer.
- If a temporary workaround is unavoidable, label it explicitly as temporary, explain the risk, and propose the permanent fix immediately.
- Prefer robust, evidence-based behavior over UI heuristics that can misclassify results.
