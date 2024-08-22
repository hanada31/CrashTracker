import click
import subprocess
import shutil
from pathlib import Path

@click.group()
def cli():
    pass

@cli.command()
@click.argument('input')
@click.argument('version')
@click.argument('output')
def generate_ets(input, version, output):
    input = Path(input)
    dir = input.parent
    output = Path(output)

    if not input.exists():
        raise click.ClickException(f"Error: {input} does not exist")
    if input.is_dir():
        raise click.ClickException(f"Error: {input} is a directory, please provide a framework file")
    
    subprocess.run(['java', '-jar', 'CrashTracker.jar', '-path', dir, '-name', input.name, '-frameworkVersion', version, '-outputDir', output], check=True)


def run_locate(file: Path, ets_path: Path, android_jars: Path, crashInfo: Path, output: Path):
    log_dir = output / 'logs'
    log_dir.mkdir(parents=True, exist_ok=True)
    dir = file.parent
    with open(log_dir / f'{file.stem}.log', 'w') as log:
        if file.suffix == '.apk':
            client = 'ApkCrashAnalysisClient'
        elif file.suffix == '.jar':
            client = 'JarCrashAnalysisClient'
        subprocess.run(['java', '-jar', 'CrashTracker_FL.jar', '-path', dir, '-name', file.name, '-androidJar', android_jars, '-strategy', 'no', '-crashInput', crashInfo, '-exceptionInput', ets_path, '-client', client, '-outputDir', output / 'output'], stdout=log, stderr=log)
    # Magic Clean
    redundant_dir = output / 'output' / file.name
    shutil.rmtree(redundant_dir)

@cli.command()
@click.argument('input')
@click.argument('reference_files_directory')
@click.argument('output')
def locate(input, reference_files_directory, output):
    input = Path(input)
    reference_files_directory = Path(reference_files_directory)
    android_jars = reference_files_directory / "AndroidJars"
    crashInfo = reference_files_directory / "CrashInfo.json"
    output = Path(output)
    work_list = []

    if not input.exists():
        click.echo(f'Error: {input} does not exist')
        return
    if input.is_dir():
        for file in input.iterdir():
            if file.is_file() and file.suffix == '.apk':
                work_list.append(file)
    if input.is_file():
        if input.suffix == '.apk' or input.suffix == '.jar':
            work_list.append(input)
        else:
            click.echo(f'Error: {input} is not an apk file')
            return

    click.echo(f'Found {len(work_list)} apk files')
    output.mkdir(parents=True, exist_ok=True)
    for file in work_list:
        click.echo(f'Processing {file.name}')
        run_locate(file, reference_files_directory / "ETS-default", android_jars, crashInfo, output / "LocalizationReport")
        run_locate(file, reference_files_directory / "ETS-allCond", android_jars, crashInfo, output / "ReferenceReport")
        click.echo(f'Finished {file.name}')


def setup_path_config(report_directory, reference_files_directory):
    report_directory = Path(report_directory)
    reference_files_directory = Path(reference_files_directory)
    localization_report_directory = report_directory / "LocalizationReport"
    reference_report_directory = report_directory / "ReferenceReport"
    output_directory = report_directory / "ExplanationReport"

    from ExplanationGenerator.config import setup_paths
    setup_paths(localization_report_directory, reference_report_directory, reference_files_directory, output_directory)

def setup_llm_config():
    from dotenv import load_dotenv
    import os

    load_dotenv()

    OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
    if OPENAI_API_KEY is None:
         raise click.ClickException("OPENAI_API_KEY is not set!")
    
    bash_url = os.getenv("OPENAI_BASH_URL")
    model = os.getenv("GPT_MODEL")
    temperature = os.getenv("TEMPERATURE")
    token_limit = os.getenv("TOKEN_LIMIT")
    seed = os.getenv("SEED")

    from ExplanationGenerator.config import setup_llm
    setup_llm(OPENAI_API_KEY, bash_url=bash_url, model=model, temperature=temperature, token_limit=token_limit, seed=seed)

def locate_is_done(report_directory):
    report_directory = Path(report_directory)
    output_directory = report_directory / "LocalizationReport"
    return (output_directory / "output").exists()

@cli.command()
@click.argument('report_directory')
@click.argument('reference_files_directory')
def precheck(report_directory, reference_files_directory):
    setup_path_config(report_directory, reference_files_directory)

    if not locate_is_done(report_directory):
        raise click.ClickException("Locate is not done, please run locate first")

    from ExplanationGenerator import precheck
    precheck.main()


def precheck_is_done(report_directory):
    report_directory = Path(report_directory)
    output_directory = report_directory / "ExplanationReport"
    return (output_directory / "summary.json").exists()

@cli.command()
@click.argument('report_directory')
@click.argument('reference_files_directory')
def explain(report_directory, reference_files_directory):
    setup_path_config(report_directory, reference_files_directory)
    setup_llm_config()

    if not precheck_is_done(report_directory):
        raise click.ClickException("Precheck is not done, please run precheck first")
    
    from ExplanationGenerator import run, report_generator
    run.main()


@cli.command()
@click.argument('report_directory')
@click.argument('reference_files_directory')
def generate_report(report_directory, reference_files_directory):
    setup_path_config(report_directory, reference_files_directory)
    setup_llm_config()

    from ExplanationGenerator import report_generator
    report_generator.main()
    

@cli.command()
@click.argument('report_directory')
@click.argument('reference_files_directory')
def generate_global_summary(report_directory, reference_files_directory):
    setup_path_config(report_directory, reference_files_directory)
    setup_llm_config()

    from ExplanationGenerator import report_global_summary_generator
    report_global_summary_generator.main()

if __name__ == '__main__':
    cli()
