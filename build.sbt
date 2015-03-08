lazy val editor = project.in(file(".")).
 aggregate(
   imperative,
   signals,
   events,
   signalsAndEventsFromEventsOnly,
   signalsAndEventsFromImperative)

lazy val commons = project.in(file("EDITOR/commons"))

lazy val imperative = project.in(file("EDITOR/imperative")).dependsOn(commons)

lazy val signals = project.in(file("EDITOR/signals")).dependsOn(commons)

lazy val events = project.in(file("EDITOR/events")).dependsOn(commons)

lazy val signalsAndEventsFromEventsOnly =
  project.in(file("EDITOR/signalsAndEventsFromEventsOnly")).dependsOn(commons)

lazy val signalsAndEventsFromImperative =
  project.in(file("EDITOR/signalsAndEventsFromImperative")).dependsOn(commons)
