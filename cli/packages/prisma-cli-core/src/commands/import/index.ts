import { Command, flags, Flags } from 'prisma-cli-engine'
import * as fs from 'fs-extra'
import { Importer } from './Importer'
import chalk from 'chalk';

export default class Import extends Command {
  static topic = 'import'
  static description = 'Import data into a service'
  static printVersionSyncWarning = true
  static flags: Flags = {
    data: flags.string({
      char: 'd',
      description: 'Path to zip or folder including data to import',
      required: true,
    }),
    ['env-file']: flags.string({
      description: 'Path to .env file to inject env vars',
      char: 'e',
    }),
    ['project']: flags.string({
      description: 'Path to Prisma definition file',
      char: 'p',
    }),
  }
  async run() {
    const { data } = this.flags
    const envFile = this.flags['env-file']
    await this.definition.load(this.flags, envFile)
    const serviceName = this.definition.service!
    const stage = this.definition.stage!

    const cluster = await this.definition.getCluster()
    this.env.setActiveCluster(cluster!)

    if (
      this.definition.definition!.databaseType &&
      this.definition.definition!.databaseType === 'document'
      ) {
        throw new Error(`Import is not yet supported for document stores. Please use the native import features of your database. 
        
        More info here: https://docs.mongodb.com/manual/reference/program/mongorestore/`)
      } else {
        this.out.log(chalk.yellow(`Warning: The \`prisma import\` command will not be further developed in the future. Please use the native import features of your database for these workflows. 
    
More info here:
MySQL: https://dev.mysql.com/doc/refman/5.7/en/mysqlimport.html
Postgres: https://www.postgresql.org/docs/10/app-pgrestore.html
`))
    }

    if (!fs.pathExistsSync(data)) {
      throw new Error(`Path ${data} does not exist`)
    }

    if (!data.endsWith('.zip') && !fs.lstatSync(data).isDirectory()) {
      throw new Error(`data must be a directory or end with .zip`)
    }

    // continue
    await this.import(
      data,
      serviceName,
      stage,
      this.definition.getToken(serviceName, stage),
      this.definition.getWorkspace() || undefined,
    )
  }

  async import(
    source: string,
    serviceName: string,
    stage: string,
    token?: string,
    workspaceSlug?: string,
  ) {
    await this.definition.load({})
    const typesString = this.definition.typesString!
    const importer = new Importer(
      source,
      typesString,
      this.client,
      this.out,
      this.config,
    )
    await importer.upload(serviceName, stage, token, workspaceSlug)
  }
}
