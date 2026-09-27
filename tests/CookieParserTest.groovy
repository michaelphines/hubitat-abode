// Compile the real driver, without running Hubitat's metadata or device lifecycle.
def source = new File(args ? args[0] : 'drivers/AbodeAlarm.groovy')
def driver = new GroovyShell().parse(source)
def cases = [
  ['single cookie', 'SESSION=abc; Path=/; HttpOnly', [SESSION: 'abc']],
  ['expiration comma', 'SESSION=abc; Expires=Thu, 01 Jan 2037 00:00:00 GMT; Path=/', [SESSION: 'abc']],
  ['joined cookies', 'SESSION=abc; Expires=Thu, 01 Jan 2037 00:00:00 GMT, token=xyz; Secure', [SESSION: 'abc', token: 'xyz']],
  ['multiple expiration dates', 'SESSION=abc; Expires=Thu, 01 Jan 2037 00:00:00 GMT, token=xyz; Expires=Fri, 02 Jan 2037 00:00:00 GMT', [SESSION: 'abc', token: 'xyz']],
  ['base64 padding', 'SESSION=YWJjZA==; HttpOnly', [SESSION: 'YWJjZA==']],
  ['embedded equals', 'token=abc=def==', [token: 'abc=def==']],
  ['empty cookie', 'SESSION=; Path=/', [SESSION: '']],
  ['repeated headers', ['SESSION=abc; HttpOnly', 'token=xyz; Secure'], [SESSION: 'abc', token: 'xyz']],
  ['whitespace', ' SESSION = abc ; Path=/, token=xyz', [SESSION: 'abc', token: 'xyz']],
  ['replacement', ['SESSION=first', 'SESSION=second'], [SESSION: 'second']],
  ['quoted value', 'SESSION="abc=="; HttpOnly', [SESSION: '"abc=="']],
  ['cookie name characters', '__Host-session-id=abc; Secure; Path=/', ['__Host-session-id': 'abc']],
]
int count = 0
cases.each { test ->
  def jar = [existing: 'retain', SESSION: 'old']
  driver.binding = new Binding([state: [cookies: jar]])
  driver.invokeMethod('storeCookies', [test[1]] as Object[])
  assert jar == ([existing: 'retain', SESSION: 'old'] + test[2]) : test[0]
  println "PASS: ${test[0]}"
  count++
}
[null, '', 'not-a-cookie', '=missing-name', 'bad name=secret', ['SESSION=new', 'malformed']].each { invalid ->
  def jar = [SESSION: 'unchanged']
  driver.binding = new Binding([state: [cookies: jar]])
  try {
    driver.invokeMethod('storeCookies', [invalid] as Object[])
    assert false : 'Malformed headers must fail explicitly'
  } catch (IllegalArgumentException error) {
    assert error.message.startsWith('Invalid Set-Cookie header:')
    assert !error.message.contains('secret')
    assert jar == [SESSION: 'unchanged'] : 'Do not partially update cookies on failure'
  }
  count++
}
println "Passed ${count} cookie parser regression cases."
