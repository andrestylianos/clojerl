-module('clojerl.IType').

-include("clojerl.hrl").

-clojure(true).
-protocol(true).

-callback '_'(any()) -> ?NIL.
