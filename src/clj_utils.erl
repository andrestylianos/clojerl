-module(clj_utils).

-include("clojerl.hrl").

-compile({no_auto_import, [throw/1, error/1]}).

-export([ char_type/1
        , char_type/2
        , parse_number/1
        , parse_symbol/1
        , desugar_meta/1

        , compare/2

        , throw/1
        , throw/2
        , throw_when/2
        , throw_when/3
        , error/1
        , error/2
        , error_when/2
        , error_when/3
        , warn_when/2
        , warn_when/3

        , group_by/2
        , nth/2
        , nth/3

        , trace_while/4
        , time/1
        , time/2
        , time/3
        , bench/3
        , bench/4

        , code_from_binary/1

        , floor/1
        , ceil/1
        , signum/1
        ]).

-define(INT_PATTERN,
        "^([-+]?)"
        "(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|"
        "([1-9][0-9]?)[rR]([0-9A-Za-z]+)|0[0-9]+)(N)?$").
-define(FLOAT_PATTERN, "^(([-+]?[0-9]+)(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?$").
-define(RATIO_PATTERN, "^([-+]?[0-9]+)/([0-9]+)$").
-define(SYMBOL_PATTERN, ":?([\\D^/].*/)?(/|[\\D^/][^/]*)").


-type char_type() :: whitespace | number | string
                   | keyword | comment | quote
                   | deref | meta | syntax_quote
                   | unquote | list | vector
                   | map | unmatched_delim | char
                   | unmatched_delim | char
                   | arg | dispatch | symbol.

%%------------------------------------------------------------------------------
%% Exported functions
%%------------------------------------------------------------------------------

-spec parse_number(binary()) -> integer() | float() | ratio().
parse_number(Number) ->
  Result = case number_type(Number) of
             int       -> parse_int(Number);
             float     -> parse_float(Number);
             ratio     -> parse_ratio(Number);
             ?NIL -> ?NIL
           end,

  case Result of
    ?NIL ->
      throw(<<"Invalid number format [", Number/binary, "]">>);
    _ ->
      Result
  end.

-spec parse_symbol(binary()) ->
  {Ns :: 'clojerl.Symbol':type(), Name :: 'clojerl.Symbol':type()}.
parse_symbol(<<>>) ->
  ?NIL;
parse_symbol(<<"::"/utf8, _/binary>>) ->
  ?NIL;
parse_symbol(<<"/">>) ->
  {?NIL, <<"/">>};
parse_symbol(Str) ->
  case binary:last(Str) of
    $: -> ?NIL;
    _ ->
      case binary:split(Str, <<"/">>) of
        [_Namespace, <<>>] ->
          case binary:split(Str, <<":">>) of
            [_, <<"/">>] -> {?NIL, Str};
            _            -> ?NIL
          end;
        [Namespace, <<"/">>] ->
          {Namespace, <<"/">>};
        [Namespace, Name] ->
          verify_symbol_name({Namespace, Name});
        [Name] ->
          verify_symbol_name({?NIL, Name})
      end
  end.


verify_symbol_name({undefined, Name} = Result) ->
  case re:run(Name, ?SYMBOL_PATTERN) of
    nomatch -> ?NIL;
    _       -> Result
  end;
verify_symbol_name({Namespace, Name} = Result) ->
  Pattern = case re:run(Namespace, "[^:].*:.+") of
              nomatch -> ?SYMBOL_PATTERN;
              _ -> "\\d+"
            end,
  case re:run(Name, Pattern) of
    nomatch -> ?NIL;
    _       -> Result
  end.

-spec char_type(non_neg_integer()) -> char_type().
char_type(X) -> char_type(X, <<>>).

-spec char_type(non_neg_integer(), binary()) -> char_type().
char_type(X, _)
  when X == $\n; X == $\t; X == $\r; X == $ ; X == $,->
  whitespace;
char_type(X, _)
  when X >= $0, X =< $9 ->
  number;
char_type(X, Y)
  when (X == $+ orelse X == $-),
       Y >= $0, Y =< $9 ->
  number;
char_type($", _) -> string;
char_type($:, _) -> keyword;
char_type($;, _) -> comment;
char_type($', _) -> quote;
char_type($@, _) -> deref;
char_type($^, _) -> meta;
char_type($`, _) -> syntax_quote;
char_type($~, _) -> unquote;
char_type($(, _) -> list;
char_type($[, _) -> vector;
char_type(${, _) -> map;
char_type(X, _)
  when X == $); X == $]; X == $} ->
  unmatched_delim;
char_type($\\, _) -> char;
char_type($%, _) -> arg;
char_type($#, _) -> dispatch;
char_type(_, _) -> symbol.

-spec desugar_meta('clojerl.Map':type() |
                   'clojerl.Keyword':type() |
                   'clojerl.Symbol':type() |
                   string()) -> map().
desugar_meta(Meta) ->
  case clj_core:type(Meta) of
    'clojerl.Keyword' ->
      clj_core:hash_map([Meta, true]);
    'clojerl.Map' ->
      Meta;
    Type when Type == 'clojerl.Symbol'
              orelse Type == 'clojerl.String' ->
      Tag = clj_core:keyword(<<"tag">>),
      clj_core:hash_map([Tag, Meta]);
    _ ->
      throw(<<"Metadata must be Symbol, Keyword, String or Map">>)
  end.

-spec compare(any(), any()) -> integer().
compare(X, Y) ->
  if
    X <  Y -> -1;
    X == Y -> 0;
    X >  Y -> 1
  end.

-spec throw(any()) -> no_return().
throw(Reason) ->
  throw(Reason, ?NIL).

-spec throw(any(), ?NIL | clj_reader:location()) -> no_return().
throw(List, Location) ->
  throw_when(true, List, Location).

-spec throw_when(boolean(), any()) -> ok | no_return().
throw_when(Throw, Reason) ->
  throw_when(Throw, Reason, ?NIL).

-spec throw_when(boolean(), any(), clj_reader:location()) -> ok | no_return().
throw_when(true, List, Location) when is_list(List) ->
  Reason = erlang:iolist_to_binary(lists:map(fun clj_core:str/1, List)),
  throw_when(true, Reason, Location);
throw_when(true, Reason, Location) when is_binary(Reason) ->
  LocationBin = location_to_binary(Location),
  erlang:throw(<<LocationBin/binary, Reason/binary>>);
throw_when(true, Reason, Location) ->
  erlang:throw({Location, Reason});
throw_when(false, _, _) ->
  ok.

-spec error(any()) -> no_return().
error(List) ->
  error_when(true, List, ?NIL).

-spec error(any(), clj_reader:location() | ?NIL) -> no_return().
error(List, Location) ->
  error_when(true, List, Location).

-spec error_when(boolean(), any()) -> ok | no_return().
error_when(Throw, Reason) ->
  error_when(Throw, Reason, ?NIL).

-spec error_when(boolean(), any(), clj_reader:location() | ?NIL) ->
  ok | no_return().
error_when(true, List, Location) when is_list(List) ->
  Reason = erlang:iolist_to_binary(lists:map(fun clj_core:str/1, List)),
  error_when(true, Reason, Location);
error_when(true, Reason, Location) when is_binary(Reason) ->
  LocationBin = location_to_binary(Location),
  erlang:error(<<LocationBin/binary, Reason/binary>>);
error_when(true, Reason, Location) ->
  erlang:error({Location, Reason});
error_when(false, _, _) ->
  ok.

-spec warn_when(boolean(), any()) -> ok | no_return().
warn_when(Warn, Reason) ->
  warn_when(Warn, Reason, ?NIL).

-spec warn_when(boolean(), any(), clj_reader:location()) -> ok | no_return().
warn_when(true, List, Location) when is_list(List) ->
  Reason = erlang:iolist_to_binary(lists:map(fun clj_core:str/1, List)),
  warn_when(true, Reason, Location);
warn_when(true, Reason, Location) when is_binary(Reason) ->
  LocationBin = location_to_binary(Location),
  'erlang.io.IWriter':write( 'clojure.core':'*err*__val'()
                           , <<LocationBin/binary, Reason/binary, "\n">>
                           );
warn_when(true, Reason, Location) ->
  'erlang.io.IWriter':write( 'clojure.core':'*err*__val'()
                           , "~p~n"
                           , [{Location, Reason}]
                           );
warn_when(false, _, _) ->
  ok.

-spec group_by(fun((any()) -> any()), list()) -> map().
group_by(GroupBy, List) ->
  Group = fun(Item, Acc) ->
              Key = GroupBy(Item),
              Items = maps:get(Key, Acc, []),
              Acc#{Key => [Item | Items]}
          end,
  Map = lists:foldl(Group, #{}, List),
  ReverseValue = fun(_, V) -> lists:reverse(V) end,
  maps:map(ReverseValue, Map).

-spec trace_while(string(), function(), [module()], timeout()) -> ok.
trace_while(Filename, Fun, Modules, Timeout) ->
  Self = self(),
  F = fun() ->
          Self ! start,
          Fun(),
          Self ! stop
      end,
  spawn(F),

  receive start -> ok
  after 1000 -> throw(<<"Fun never started">>)
  end,

  {{Y, M, D}, {Hours, Mins, Secs}} = calendar:local_time(),
  FilenameUnique = io_lib:format( "~s-~p-~p-~p-~p-~p-~p"
                                , [Filename, Y, M, D, Hours, Mins, Secs]
                                ),
  eep:start_file_tracing(FilenameUnique, [], Modules),

  receive stop -> ok
  after Timeout -> ok
  end,

  eep:stop_tracing(),
  eep:convert_tracing(FilenameUnique).

-spec time(function()) -> ok.
time(Fun) when is_function(Fun) ->
  time("Time", Fun).

-spec time(string(), function()) -> ok.
time(Label, Fun) when is_function(Fun) ->
  time(Label, Fun, []).

-spec time(string(), function(), list()) -> ok.
time(Label, Fun, Args) ->
  {T, V} = timer:tc(fun() -> apply(Fun, Args) end),
  io:format("~s: ~p ms~n", [Label, T / 1000]),
  V.

bench(Name, Fun, Trials) ->
  bench(Name, Fun, [], Trials).

bench(Name, Fun, Args, Trials) ->
    print_result(Name, repeat_tc(Fun, Args, Trials)).

repeat_tc(Fun, Args, Trials) ->
  Repeat = fun
             R(0) -> ok;
             R(N) -> apply(Fun, Args), R(N - 1)
           end,

    {Time, _} = timer:tc(fun() -> Repeat(Trials) end),
    {Time, Trials}.

print_result(Name, {Time, Trials}) ->
    io:format("~s: ~.3f ms (~.2f per second)~n",
              [Name, (Time / 1000) / Trials, Trials / (Time / 1000000)]).

-spec code_from_binary(atom()) -> cerl:cerl() | {error, term()}.
code_from_binary(Name) when is_atom(Name) ->
  case code:get_object_code(Name) of
    {Name, Binary, _} ->
      core_from_binary(Binary);
    _ ->
      clj_utils:error([ <<"Could not load object code for namespace: ">>
                      , atom_to_binary(Name, utf8)
                      ])
  end.

-spec core_from_binary(binary()) ->
  cerl:cerl() | {error, missing_abstract_code}.
core_from_binary(Binary) ->
  ChunkNames = ["Core", abstract_code],
  ChunkOpts  = [allow_missing_chunks],
  {ok, {_, Chunks}} = beam_lib:chunks(Binary, ChunkNames, ChunkOpts),
  case proplists:get_value("Core", Chunks) of
    missing_chunk ->
      case proplists:get_value(abstract_code, Chunks) of
        %% This case is only for bootstrapping clojure.core since it
        %% is written in Erlang it has erlang abstract syntax forms.
        {raw_abstract_v1, Code} ->
          {Mod, Exp, Forms, Opts} = sys_pre_expand:module(Code, []),
          {ok, CoreModule, _} = v3_core:module({Mod, Exp, Forms}, Opts),
          CoreModule;
        missing_chunk ->
          {error, missing_abstract_code}
        end;
    CoreModule ->
      erlang:binary_to_term(CoreModule)
  end.

%%------------------------------------------------------------------------------
%% Internal helper functions
%%------------------------------------------------------------------------------

%% @doc Valid integers can be either in decimal, octal, hexadecimal or any
%%      base specified (e.g. `2R010` is binary for `2`).
-spec parse_int(binary()) -> integer() | ?NIL.
parse_int(IntBin) ->
  {match, [_ | Groups]} = re:run(IntBin, ?INT_PATTERN, [{capture, all, list}]),
  case int_properties(Groups) of
    {zero, _Arbitrary, _Negate} ->
      0;
    {{Base, Value}, _Arbitrary, Negate} ->
      list_to_integer(Value, Base) * Negate;
    _ ->
      ?NIL
  end.

-spec int_properties([string()]) -> {zero | ?NIL | {integer(), string()},
                                     boolean(),
                                     boolean()}.
int_properties(Groups) ->
  Props = lists:map(fun(X) -> X =/= "" end, Groups),
  Result =
    case Props of
      [_, true | _] -> zero;
      [_, _, true | _]-> {10, nth(3, Groups)};
      [_, _, _, true | _]-> {16, nth(4, Groups)};
      [_, _, _, _, true | _]-> {8, nth(5, Groups)};
      [_, _, _, _, _, _, true | _]->
        Base = list_to_integer(lists:nth(6, Groups)),
        {Base, lists:nth(7, Groups)};
      _ ->
        ?NIL
    end,

  Arbitrary = nth(8, Props, false),
  Negate = nth(1, Props),
  {Result, Arbitrary, case Negate of true -> -1; false -> 1 end}.

-spec parse_float(binary()) -> float().
parse_float(FloatBin) ->
  {match, [_ | Groups]} =
    re:run(FloatBin, ?FLOAT_PATTERN, [{capture, all, list}]),

  %% When there is no decimal part we add it so we can use
  %% list_to_float/1.
  FloatStr = case nth(3, Groups, "") of
               ""  -> nth(2, Groups) ++ ".0" ++ nth(4, Groups, "");
               "." -> nth(2, Groups) ++ ".0" ++ nth(4, Groups, "");
               _   -> nth(1, Groups)
             end,

  list_to_float(FloatStr).

-type ratio() :: #{type => ratio,
                   denom => integer(),
                   enum => integer()}.

-spec parse_ratio(binary()) -> ratio().
parse_ratio(RatioBin) ->
  {match, [_ | Groups]} =
    re:run(RatioBin, ?RATIO_PATTERN, [{capture, all, list}]),
  Numerator = nth(1, Groups),
  Denominator = nth(2, Groups),
  {ratio,
   list_to_integer(Numerator),
   list_to_integer(Denominator)}.

number_type(Number) ->
  Regex = #{int   => ?INT_PATTERN,
            float => ?FLOAT_PATTERN,
            ratio => ?RATIO_PATTERN},
  Fun = fun(Type, RE, Acc) ->
            case re:run(Number, RE) of
              nomatch -> Acc;
              _ -> [Type | Acc]
            end
        end,
  case maps:fold(Fun, [], Regex) of
    [] -> ?NIL;
    [T | _] -> T
  end.

%% @doc Like lists:nth/2 but returns nil if `Index` is
%%      larger than the amount of elements in `List`.
nth(Index, List) ->
  nth(Index, List, ?NIL).

nth(Index, List, Default) ->
  case Index =< length(List) of
    true -> lists:nth(Index, List);
    false -> Default
  end.

-spec location_to_binary(?NIL | clj_reader:location()) -> binary().
location_to_binary(#{line := Line, column := Col, file := Filename})
  when is_integer(Line) andalso is_integer(Col) ->
  LineBin     = integer_to_binary(Line),
  ColBin      = integer_to_binary(Col),
  FilenameBin = case Filename of
                  ?NIL -> <<"?">>;
                  _ -> Filename
                end,
  <<FilenameBin/binary, ":", LineBin/binary, ":", ColBin/binary, ": ">>;
location_to_binary(#{line := Line, column := Col} = Location)
  when is_integer(Line) andalso is_integer(Col) ->
  location_to_binary(Location#{file => ?NIL});
location_to_binary(_) ->
  <<"?:?:?: ">>.

-spec floor(number()) -> number().
floor(X) when X < 0 ->
  T = trunc(X),
  case X - T == 0 of
    true  -> T;
    false -> T - 1
  end;
floor(X) ->
  trunc(X).

-spec ceil(number()) -> number().
ceil(X) when X < 0 ->
  trunc(X);
ceil(X) ->
  T = trunc(X),
  case X - T == 0 of
    true  -> T;
    false -> T + 1
  end.

-spec signum(number()) -> number().
signum(X) when X < 0 -> -1;
signum(X) when X >= 0 -> 1.
