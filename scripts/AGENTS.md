# Root scripts

Keep this directory small. It contains only project-wide orchestration:

- `dev.sh`: local setup, services, database, and tmux development session
- `generate-api.sh`: OpenAPI and frontend client generation
- `docs.py`: numbered documentation validation and cumulative Markdown/PDF generation
- `images.sh`: Docker image build and push
- `archive.sh`: source archive creation
- `common.sh`: shared shell helpers

Application-specific commands belong in the application directory. Test and CI orchestration does not belong in the root command surface.
