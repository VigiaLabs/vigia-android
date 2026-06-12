
# --- Claude Automation Harness ---
checkpoint:
	@echo "Automating session handoff snapshot..."
	@echo "# Automated Session Handoff - $$(date)" > session-handoff.md
	@echo "## Modified Files in Last Session:" >> session-handoff.md
	@git diff --name-status HEAD~1 >> session-handoff.md || echo "No previous commit found" >> session-handoff.md
	@echo "" >> session-handoff.md
	@echo "## Active Implementation State:" >> session-handoff.md
	@git log -1 --pretty=format:"Last Commit Message: %s (%an)" >> session-handoff.md || echo "No commits yet" >> session-handoff.md
	@echo "" >> session-handoff.md
	@echo "[SYSTEM INSTRUCTION]: Next session must read this file, verify compilation via 'make', and pull dependencies via codebase-memory-mcp." >> session-handoff.md
	@echo "Done: session-handoff.md compiled."
