name: Main Pipeline

on:
  push:
    branches: [main]

env:
  PROJECT_NAME: inct
  ENVIRONMENT: dev
  STORAGE_ACCOUNT_NAME: stgeu2nfmcd03

jobs:
  build:
    uses: ./.github/workflows/reusable/build-angular-app.yml
    with:
      node-version: '10'
      environment: ${{ env.ENVIRONMENT }}

  jscrambler:
    needs: build
    uses: ./.github/workflows/reusable/run-jscrambler.yml
    with:
      jscrambler-api-url: 'imu30977-82.jscrambler.com'
      jscrambler-api-version: '8.2'
      jscrambler-access-key: ${{ secrets.JSCRAMBLER_ACCESS_KEY }}
      jscrambler-secret-key: ${{ secrets.JSCRAMBLER_SECRET_KEY }}

  sast:
    needs: jscrambler
    uses: ./.github/workflows/reusable/run-sast-analysis.yml
    with:
      sast-tool: 'sast-tool-command'

  deploy:
    needs: sast
    uses: ./.github/workflows/reusable/deploy-to-azure-storage.yml
    with:
      storage-account: ${{ env.STORAGE_ACCOUNT_NAME }}
      source-path: dist
    secrets:
      AZURE_STORAGE_ACCOUNT: ${{ secrets.AZURE_STORAGE_ACCOUNT }}
      AZURE_STORAGE_KEY: ${{ secrets.AZURE_STORAGE_KEY }}

  notify:
    needs: deploy
    uses: ./.github/workflows/reusable/notify-result.yml
    with:
      status: 'SUCCESS'
      recipients: 'recipients@example.com'