def log = new File(basedir, 'build.log').text.replace('\\', '/') // paths are displayed with the host separator
assert log.contains("Validating api/openapi.yaml using lint rules for api 'petstore'")
assert log.contains('1 API description validated: 0 errors, 1 warning')
assert log.contains('no-server-example.com')
assert log.contains('Lint report (junit) written to target/redocly/lint.xml')
assert log.contains("Created bundle for api/openapi.yaml using configuration for api 'petstore' at target/generated-resources/openapi/petstore.yaml")
assert log.contains('Attached target/generated-resources/openapi/petstore.yaml as artifact (type=yaml, classifier=petstore)')
assert log.contains('BUILD SUCCESS')

def report = new File(basedir, 'target/redocly/lint.xml').text
assert report.contains('<testsuite')
assert report.contains('no-server-example.com')

def bundle = new File(basedir, 'target/generated-resources/openapi/petstore.yaml').text
assert bundle.startsWith('openapi: 3.0.3')
assert bundle.contains('  description: |\n    This description was injected by a decorator.')
assert bundle.contains("\$ref: '#/components/schemas/pet'")
assert !bundle.contains('schemas/pet.yaml')

// addResource=true: the bundle ends up in the jar
def jar = new java.util.jar.JarFile(new File(basedir, 'target/lint-and-bundle-1.0-SNAPSHOT.jar'))
assert jar.getEntry('petstore.yaml') != null
jar.close()
return true
